package me.vripper.services.download

import dev.failsafe.function.CheckedRunnable
import kotlinx.coroutines.*
import me.vripper.entities.ImageEntity
import me.vripper.entities.Status
import me.vripper.exception.DownloadException
import me.vripper.exception.HostException
import me.vripper.host.DownloadedImage
import me.vripper.host.Host
import me.vripper.host.ImageMimeType
import me.vripper.model.Settings
import me.vripper.services.DataAccessService
import me.vripper.services.HTTPService
import me.vripper.services.VGAuthService
import me.vripper.utilities.ApplicationProperties
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils.getExtension
import me.vripper.utilities.PathUtils.getFileNameWithoutExtension
import me.vripper.utilities.PathUtils.sanitize
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines

internal class ImageDownloadRunnable(
    imageEntity: ImageEntity, settings: Settings
) : KoinComponent, CheckedRunnable {

    class Context(val imageEntity: ImageEntity, val settings: Settings) {

        private val log by LoggerDelegate()
        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val jobs = mutableListOf<Job>()

        val httpContext: HttpClientContext = HttpClientContext.create().apply {
            cookieStore = HTTPService.cookieStore
        }
        val requests = mutableListOf<HttpUriRequestBase>()

        init {
            ApplicationProperties.VRIPPER_DIR.listDirectoryEntries()
                .filter { it.fileName.pathString.startsWith("cookies") }.forEach { cookiesPath ->
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

        private fun loadCookies(cookiesPath: Path): List<Cookie> {
            return cookiesPath.readLines().map { it.trim() }.filter { it.isNotBlank() }.filter { !it.startsWith("#") }
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

        fun clear() {
            runBlocking {
                requests.forEach { it.abort() }
                coroutineScope.cancel()
                jobs.forEach { job -> job.cancelAndJoin() }
                jobs.clear()
            }
        }

        fun launchCoroutine(block: suspend CoroutineScope.() -> Unit): Job {
            return coroutineScope.launch(block = block).also { job -> jobs.add(job) }
        }
    }

    private val log by LoggerDelegate()
    private val dataAccessService: DataAccessService by inject()
    private val vgAuthService: VGAuthService by inject()
    private val hosts: List<Host> = getKoin().getAll()
    val context = Context(imageEntity, settings)
    var completed = false
    var stopped = false

    fun download() {
        try {
            context.imageEntity.status = Status.DOWNLOADING
            context.imageEntity.downloaded = 0
            dataAccessService.updateImage(context.imageEntity)
            synchronized(context.imageEntity.postEntityId.toString().intern()) {
                val post = dataAccessService.findPostByEntityId(context.imageEntity.postEntityId)
                if (post.status != Status.DOWNLOADING) {
                    post.status = Status.DOWNLOADING
                    dataAccessService.updatePost(post)
                    vgAuthService.leaveThanks(post)
                }
            }
            log.debug("Getting image url and name from ${context.imageEntity.url} using ${context.imageEntity.host}")
            val host = hosts.first { it.isSupported(context.imageEntity.url) }
            val downloadedImage = host.downloadInternal(context)
            log.debug("Resolved name for ${context.imageEntity.url}: ${downloadedImage.name}")
            log.debug("Downloaded image {} to {}", context.imageEntity.url, downloadedImage.path)
            synchronized(context.imageEntity.postEntityId.toString().intern()) {
                val post = dataAccessService.findPostByEntityId(context.imageEntity.postEntityId)
                val downloadDirectory = Path(post.downloadDirectory, post.folderName).pathString
                checkImageTypeAndRename(
                    downloadDirectory, downloadedImage, context.imageEntity.index
                )
                if (context.imageEntity.downloaded == context.imageEntity.size && context.imageEntity.size > 0) {
                    context.imageEntity.status = Status.FINISHED
                    post.done += 1
                    post.downloaded += context.imageEntity.size
                    dataAccessService.updatePost(post)
                } else {
                    context.imageEntity.status = Status.ERROR
                }
                dataAccessService.updateImage(context.imageEntity)
            }
        } catch (e: Exception) {
            if (stopped) {
                return
            }
            context.imageEntity.status = Status.ERROR
            dataAccessService.updateImage(context.imageEntity)
            throw DownloadException(e)
        }
    }

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
        val filename = if (existingExtension.isBlank()) "${sanitize(downloadedImage.name)}.$extension" else "${
            sanitize(
                fileNameWithoutExtension
            )
        }.$extension"
        try {
            val downloadDestinationFolder = Path.of(downloadDirectory)
            Files.createDirectories(downloadDestinationFolder)
            val finalFilename = "${
                if (context.settings.downloadSettings.forceOrder) String.format(
                    "%03d_", index + 1
                ) else ""
            }$filename"
            context.imageEntity.filename = finalFilename
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
        try {
            if (stopped) {
                return
            }
            download()
        } finally {
            completed = true
            context.clear()
        }
    }

    fun stop() {
        stopped = true
        context.clear()
        dataAccessService.updateImage(context.imageEntity)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ImageDownloadRunnable
        return context.imageEntity.id == that.context.imageEntity.id
    }

    override fun hashCode(): Int {
        return Objects.hash(context.imageEntity.id)
    }
}