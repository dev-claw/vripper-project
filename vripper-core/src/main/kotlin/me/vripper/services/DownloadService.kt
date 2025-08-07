package me.vripper.services

import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import dev.failsafe.function.CheckedRunnable
import kotlinx.coroutines.*
import me.vripper.entities.ImageEntity
import me.vripper.entities.PostEntity
import me.vripper.entities.Status
import me.vripper.event.ErrorCountEvent
import me.vripper.event.EventBus
import me.vripper.event.QueueStateEvent
import me.vripper.event.StoppedEvent
import me.vripper.exception.DownloadException
import me.vripper.exception.HostException
import me.vripper.host.DownloadedImage
import me.vripper.host.Host
import me.vripper.host.ImageMimeType
import me.vripper.model.ErrorCount
import me.vripper.model.QueueState
import me.vripper.model.Settings
import me.vripper.utilities.ApplicationProperties
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils.getExtension
import me.vripper.utilities.PathUtils.getFileNameWithoutExtension
import me.vripper.utilities.PathUtils.sanitize
import me.vripper.utilities.downloadRunner
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines

internal class DownloadService(
    private val settingsService: SettingsService,
    private val dataTransaction: DataTransaction,
    private val retryPolicyService: RetryPolicyService,
    private val eventBus: EventBus
) {

    private val maxPoolSize: Int = 24
    private val log by LoggerDelegate()
    private val running: MutableMap<Byte, MutableList<ImageDownloadRunnable>> = mutableMapOf()
    private val pending: MutableMap<Byte, MutableList<ImageDownloadRunnable>> = mutableMapOf()
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private var downloadMonitorThread: Thread? = null

    internal class ImageDownloadContext(val imageEntity: ImageEntity, val settings: Settings) : KoinComponent {
        private val log by LoggerDelegate()

        init {
            ApplicationProperties.VRIPPER_DIR
                .listDirectoryEntries()
                .filter { it.fileName.pathString.startsWith("cookies") }
                .forEach { cookiesPath ->
                    loadCookies(cookiesPath).also { cookies ->
                        cookies.forEach { cookie ->
                            if (HTTPService.cookieStore.cookies.find { it.name == cookie.name } == null) {
                                log.info("Applying cookie: ${cookie.name}")
                                HTTPService.cookieStore.addCookie(cookie)
                            } else {
                                log.warn("Cookie already loaded: ${cookie.name}")
                            }
                        }
                    }
                }
        }

        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val jobs = mutableListOf<Job>()
        val httpContext: HttpClientContext = HttpClientContext.create().apply {
            cookieStore = HTTPService.cookieStore
        }


        private fun loadCookies(cookiesPath: Path): List<Cookie> {
            return cookiesPath.readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { !it.startsWith("#") }
                .map { line ->
                    val cookieComponents = line.split("\t")
                    BasicClientCookie(cookieComponents[5], cookieComponents[6]).apply {
                        domain = cookieComponents[0]
                        isHttpOnly = cookieComponents[1].toBoolean()
                        path = cookieComponents[2]
                        isSecure = cookieComponents[3].toBoolean()
                        setExpiryDate(Instant.ofEpochSecond(cookieComponents[4].toLong()))
                    }
                }.also {
                    log.info("Found ${it.size} cookies in $cookiesPath")
                }
        }

        val requests = mutableListOf<HttpUriRequestBase>()
        val postId = imageEntity.postIdRef

        fun cancelCoroutines() {
            runBlocking {
                coroutineScope.cancel()
                jobs.forEach { job -> job.cancelAndJoin() }
            }
        }

        fun launchCoroutine(block: suspend CoroutineScope.() -> Unit): Job {
            return coroutineScope.launch(block = block).also { job -> jobs.add(job) }
        }
    }

    internal class ImageDownloadRunnable(
        val imageEntity: ImageEntity, val postRank: Int, private val settings: Settings
    ) : KoinComponent, CheckedRunnable {
        private val log by LoggerDelegate()
        private val dataTransaction: DataTransaction by inject()
        private val vgauthService: VGAuthService by inject()
        private val hosts: List<Host> = getKoin().getAll()
        var completed = false
        var stopped = false

        private lateinit var context: ImageDownloadContext

        fun download() {
            try {
                imageEntity.status = Status.DOWNLOADING
                imageEntity.downloaded = 0
                dataTransaction.updateImage(imageEntity)
                synchronized(imageEntity.postId.toString().intern()) {
                    val post = dataTransaction.findPostById(context.postId)
                    if (post.status != Status.DOWNLOADING) {
                        post.status = Status.DOWNLOADING
                        dataTransaction.updatePost(post)
                        vgauthService.leaveThanks(post)
                    }
                }
                log.debug("Getting image url and name from ${imageEntity.url} using ${imageEntity.host}")
                val host = hosts.first { it.isSupported(imageEntity.url) }
                val downloadedImage = host.downloadInternal(imageEntity, context)
                log.debug("Resolved name for ${imageEntity.url}: ${downloadedImage.name}")
                log.debug("Downloaded image {} to {}", imageEntity.url, downloadedImage.path)
                synchronized(imageEntity.postId.toString().intern()) {
                    val post = dataTransaction.findPostById(context.postId)
                    val downloadDirectory = Path(post.downloadDirectory, post.folderName).pathString
                    checkImageTypeAndRename(
                        downloadDirectory, downloadedImage, imageEntity.index
                    )
                    if (imageEntity.downloaded == imageEntity.size && imageEntity.size > 0) {
                        imageEntity.status = Status.FINISHED
                        post.done += 1
                        post.downloaded += imageEntity.size
                        dataTransaction.updatePost(post)
                    } else {
                        imageEntity.status = Status.ERROR
                    }
                    dataTransaction.updateImage(imageEntity)
                }
            } catch (e: Exception) {
                if (stopped) {
                    return
                }
                imageEntity.status = Status.ERROR
                dataTransaction.updateImage(imageEntity)
                throw DownloadException(e)
            }
        }

        @Throws(HostException::class)
        private fun checkImageTypeAndRename(
            downloadDirectory: String, downloadedImage: DownloadedImage, index: Int
        ) {
            val existingExtension = getExtension(downloadedImage.name).lowercase()
            val fileNameWithoutExtension = getFileNameWithoutExtension(downloadedImage.name)
            val extension = when (downloadedImage.type) {
                ImageMimeType.IMAGE_BMP -> "BMP"
                ImageMimeType.IMAGE_GIF -> "GIF"
                ImageMimeType.IMAGE_JPEG -> "JPG"
                ImageMimeType.IMAGE_PNG -> "PNG"
                ImageMimeType.IMAGE_WEBP -> "WEBP"
            }
            val filename =
                if (existingExtension.isBlank()) "${sanitize(downloadedImage.name)}.$extension" else "${
                    sanitize(
                        fileNameWithoutExtension
                    )
                }.$extension"
            try {
                val downloadDestinationFolder = Path.of(downloadDirectory)
                Files.createDirectories(downloadDestinationFolder)
                val finalFilename = "${
                    if (settings.downloadSettings.forceOrder) String.format(
                        "%03d_", index + 1
                    ) else ""
                }$filename"
                imageEntity.filename = finalFilename
                val imageDownloadPath = downloadDestinationFolder.resolve(finalFilename)
                Files.copy(downloadedImage.path, imageDownloadPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                throw HostException("Failed to rename the image", e)
            } finally {
                try {
                    Files.delete(downloadedImage.path)
                } catch (_: IOException) {
                }
            }
        }

        override fun run() {
            context = ImageDownloadContext(imageEntity, settings)
            try {
                if (stopped) {
                    return
                }
                download()
            } finally {
                completed = true
                context.cancelCoroutines()
            }
        }

        fun stop() {
            stopped = true
            context.requests.forEach { it.abort() }
            context.cancelCoroutines()
            dataTransaction.updateImage(context.imageEntity)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as ImageDownloadRunnable
            return imageEntity.id == that.imageEntity.id
        }

        override fun hashCode(): Int {
            return Objects.hash(imageEntity.id)
        }
    }

    fun init() {
        downloadMonitorThread?.interrupt()
        downloadMonitorThread = Thread.ofVirtual().name("Download Monitor").unstarted(Runnable {
            log.info("Scheduler have been initialized")
            val accepted: MutableList<ImageDownloadRunnable> = mutableListOf()
            val candidates: MutableList<ImageDownloadRunnable> = mutableListOf()
            while (!Thread.currentThread().isInterrupted) {
                lock.withLock {
                    candidates.addAll(getCandidates(candidateCount()))
                    candidates.forEach {
                        if (canRun(it.imageEntity.host)) {
                            accepted.add(it)
                            running[it.imageEntity.host]!!.add(it)
                            log.debug("${it.imageEntity.url} accepted to run")
                        }
                    }
                    accepted.forEach {
                        pending[it.imageEntity.host]?.remove(it)
                        scheduleForDownload(it)
                    }
                    accepted.clear()
                    candidates.clear()
                    try {
                        condition.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
            log.info("Scheduler have been shutdown")
        })
        downloadMonitorThread?.start()
    }

    fun halt() {
        downloadMonitorThread?.interrupt()
    }

    fun stop(postIds: List<Long> = emptyList()) {
        if (postIds.isNotEmpty()) {
            stopInternal(postIds)
            eventBus.publishEvent(StoppedEvent(postIds))
        } else {
            stopAll()
            eventBus.publishEvent(StoppedEvent(listOf(-1)))
        }
    }

    fun restartAll(postEntityIds: List<PostEntity> = emptyList()) {
        if (postEntityIds.isNotEmpty()) {
            restart(postEntityIds.associateWith { dataTransaction.findByPostIdAndIsNotCompleted(it.postId) })
        } else {
            restart(
                dataTransaction.findAllPosts()
                    .associateWith { dataTransaction.findByPostIdAndIsNotCompleted(it.postId) })
        }
    }

    private fun restart(posts: Map<PostEntity, List<ImageEntity>>) {
        lock.withLock {
            val toProcess = mutableMapOf<PostEntity, List<ImageEntity>>()

            for ((post, images) in posts) {
                if (images.isEmpty()) {
                    continue
                }
                if (isPending(post.postId)) {
                    continue
                }
                toProcess[post] = images
            }

            toProcess.forEach { (post, images) ->
                post.status = Status.PENDING
                images.forEach { image ->
                    with(image) {
                        this.status = Status.PENDING
                        this.downloaded = 0
                    }
                }
            }

            transaction {
                dataTransaction.updatePosts(
                    toProcess.keys.toList()
                )
                dataTransaction.updateImages(
                    toProcess.values.flatten()
                )
            }


            toProcess.entries.forEach { (post, images) ->
                images.forEach { image ->
                    log.debug("Enqueuing a job for ${image.url}")
                    val imageDownloadRunnable = ImageDownloadRunnable(
                        image, post.rank, settingsService.settings
                    )
                    pending.computeIfAbsent(
                        image.host
                    ) { mutableListOf() }
                    pending[image.host]!!.add(imageDownloadRunnable)
                }
            }

            condition.signal()
        }
    }

    private fun isPending(postId: Long): Boolean {
        return pending.values.flatten().any { it.imageEntity.postId == postId }
    }

    private fun isRunning(postId: Long): Boolean {
        return running.values.flatten().any { it.imageEntity.postId == postId }
    }

    private fun stopAll() {
        lock.withLock {
            pending.values.clear()
            running.values.flatten().forEach { obj: ImageDownloadRunnable -> obj.stop() }
            while (running.values.flatten().count { !it.completed } > 0) {
                Thread.sleep(100)
            }
            dataTransaction.findAllNonCompletedPostIds().forEach {
                dataTransaction.stopImagesByPostIdAndIsNotCompleted(it)
                dataTransaction.finishPost(it)
            }
        }
    }

    private fun stopInternal(postIds: List<Long>) {
        lock.withLock {
            for (postId in postIds) {
                pending.values.forEach { pending ->
                    pending.removeIf { it.imageEntity.postId == postId }
                }
                running.values.flatten()
                    .filter { p: ImageDownloadRunnable -> p.imageEntity.postId == postId }
                    .forEach { obj: ImageDownloadRunnable -> obj.stop() }
                while (running.values.flatten()
                        .count { !it.completed && it.imageEntity.postId == postId } > 0
                ) {
                    Thread.sleep(100)
                }
            }
            postIds.forEach {
                dataTransaction.stopImagesByPostIdAndIsNotCompleted(it)
                dataTransaction.finishPost(it)
            }
        }
    }

    private fun canRun(host: Byte): Boolean {
        val totalRunning = running.values.sumOf { it.size }
        return (running[host]!!.size < settingsService.settings.connectionSettings.maxConcurrentPerHost && if (settingsService.settings.connectionSettings.maxGlobalConcurrent == 0) totalRunning < maxPoolSize else totalRunning < settingsService.settings.connectionSettings.maxGlobalConcurrent)
    }

    private fun candidateCount(): Map<Byte, Int> {
        val map: MutableMap<Byte, Int> = mutableMapOf()
        Host.Companion.getHosts().values.forEach { host: Byte ->
            val imageDownloadRunnableList: List<ImageDownloadRunnable> = running.computeIfAbsent(
                host
            ) { mutableListOf() }
            val count: Int =
                settingsService.settings.connectionSettings.maxConcurrentPerHost - imageDownloadRunnableList.size
            log.debug("Download slots for $host: $count")
            map[host] = count
        }
        return map
    }

    private fun getCandidates(candidateCount: Map<Byte, Int>): List<ImageDownloadRunnable> {
        val hostIntegerMap: MutableMap<Byte, Int> = candidateCount.toMutableMap()
        val candidates: MutableList<ImageDownloadRunnable> = mutableListOf()
        hosts@ for (host in pending.keys) {

            val list: List<ImageDownloadRunnable> =
                pending[host]!!.sortedWith(Comparator.comparingInt<ImageDownloadRunnable> { it.postRank }
                    .thenComparingInt { it.imageEntity.index })

            for (imageDownloadRunnable in list) {
                val count = hostIntegerMap[host] ?: 0
                if (count > 0) {
                    candidates.add(imageDownloadRunnable)
                    hostIntegerMap[host] = count - 1
                } else {
                    continue@hosts
                }
            }
        }
        return candidates
    }

    private fun scheduleForDownload(imageDownloadRunnable: ImageDownloadRunnable) {
        log.debug("Scheduling a job for ${imageDownloadRunnable.imageEntity.url}")
        eventBus.publishEvent(QueueStateEvent(QueueState(runningCount(), pendingCount())))
        Failsafe.with<Any, RetryPolicy<Any>>(retryPolicyService.buildRetryPolicy("Failed to download ${imageDownloadRunnable.imageEntity.url}: "))
            .with(downloadRunner)
            .onFailure {
                log.error(
                    "Failed to download ${imageDownloadRunnable.imageEntity.url} after ${it.attemptCount} tries",
                    it.exception
                )
                val image = imageDownloadRunnable.imageEntity
                image.status = Status.ERROR
                dataTransaction.updateImage(image)
            }
            .onComplete {
                afterJobFinish(imageDownloadRunnable)
                eventBus.publishEvent(
                    QueueStateEvent(
                        QueueState(
                            runningCount(), pendingCount()
                        )
                    )
                )
                eventBus.publishEvent(ErrorCountEvent(ErrorCount(dataTransaction.countImagesInError())))
                log.debug(
                    "Finished downloading ${imageDownloadRunnable.imageEntity.url}"
                )
            }.runAsync(imageDownloadRunnable)
    }

    private fun afterJobFinish(imageDownloadRunnable: ImageDownloadRunnable) {
        lock.withLock {
            val image = imageDownloadRunnable.imageEntity
            running[image.host]!!.remove(imageDownloadRunnable)
            if (!isPending(image.postId) && !isRunning(
                    image.postId
                ) && !imageDownloadRunnable.stopped
            ) {
                dataTransaction.finishPost(image.postId, true)
            }
            condition.signal()
        }
    }

    private fun pendingCount(): Int {
        return pending.values.sumOf { it.size }
    }

    private fun runningCount(): Int {
        return running.values.sumOf { it.size }
    }
}