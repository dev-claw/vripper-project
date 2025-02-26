package me.vripper.download

import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import kotlinx.coroutines.Runnable
import me.vripper.entities.ImageEntity
import me.vripper.entities.PostEntity
import me.vripper.entities.Status
import me.vripper.event.ErrorCountEvent
import me.vripper.event.EventBus
import me.vripper.event.QueueStateEvent
import me.vripper.event.StoppedEvent
import me.vripper.host.Host
import me.vripper.model.ErrorCount
import me.vripper.model.QueueState
import me.vripper.services.DataTransaction
import me.vripper.services.RetryPolicyService
import me.vripper.services.SettingsService
import me.vripper.services.VGAuthService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.downloadRunner
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DownloadService(
    private val settingsService: SettingsService,
    private val dataTransaction: DataTransaction,
    private val retryPolicyService: RetryPolicyService,
    private val vgAuthService: VGAuthService,
    private val eventBus: EventBus
) {
    private val maxPoolSize: Int = 24
    private val log by LoggerDelegate()
    private val running: MutableMap<Byte, MutableList<ImageDownloadRunnable>> = mutableMapOf()
    private val pending: MutableMap<Byte, MutableList<ImageDownloadRunnable>> = mutableMapOf()
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    init {
        Thread.ofVirtual().name("Download Loop").unstarted(Runnable {
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
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }).start()
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
        Host.getHosts().values.forEach { host: Byte ->
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