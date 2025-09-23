package me.vripper.services.download

import me.vripper.entities.ImageEntity
import me.vripper.entities.PostEntity
import me.vripper.entities.Status
import me.vripper.event.EventBus
import me.vripper.event.StoppedEvent
import me.vripper.services.DataAccessService
import me.vripper.services.SettingsService
import me.vripper.services.download.SharedLock.downloadManagerCondition
import me.vripper.services.download.SharedLock.downloadManagerLock
import me.vripper.utilities.LoggerDelegate
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.concurrent.withLock
import kotlin.math.min

internal class DownloadService(
    private val settingsService: SettingsService,
    private val dataAccessService: DataAccessService,
    private val queueManager: QueueManager,
    private val eventBus: EventBus
) {

    private val maxPoolSize: Int = 24
    private val log by LoggerDelegate()
    private var downloadMonitorThread: Thread? = null

    fun init() {
        downloadMonitorThread?.interrupt()
        downloadMonitorThread = Thread.ofVirtual().name("Download Monitor").unstarted(Runnable {
            log.info("DownloadManager have been initialized")
            while (!Thread.currentThread().isInterrupted) {
                downloadManagerLock.withLock {
                    val candidates = getCandidates()
                    val canRunPerHost = canRun(candidates.keys)
                    val accepted = candidates.entries.flatMap {
                        it.value.take(canRunPerHost[it.key] ?: 0)
                    }
                    queueManager.accept(accepted)
                    try {
                        downloadManagerCondition.await()
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

    fun stop(postEntityIds: List<Long> = emptyList()) {
        if (postEntityIds.isNotEmpty()) {
            stopInternal(postEntityIds)
            eventBus.publishEvent(StoppedEvent(postEntityIds))
        } else {
            stopAll()
            eventBus.publishEvent(StoppedEvent(listOf(-1)))
        }
    }

    fun restartAll(postEntities: List<PostEntity> = emptyList()) {
        if (postEntities.isNotEmpty()) {
            restart(postEntities.associateWith { dataAccessService.findByPostEntityIdAndIsNotCompleted(it.id) })
        } else {
            restart(
                dataAccessService.findAllPosts()
                    .associateWith { dataAccessService.findByPostEntityIdAndIsNotCompleted(it.id) })
        }
    }

    fun move(postEntityId: Long, position: MovePosition) {
        queueManager.move(postEntityId, position)
    }

    private fun restart(posts: Map<PostEntity, List<ImageEntity>>) {
        downloadManagerLock.withLock {
            val toProcess = mutableMapOf<PostEntity, List<ImageEntity>>()

            for ((post, images) in posts) {
                if (images.isEmpty()) {
                    continue
                }
                if (queueManager.isPending(post.id)) {
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
                dataAccessService.updatePosts(
                    toProcess.keys.toList()
                )
                dataAccessService.updateImages(
                    toProcess.values.flatten()
                )
            }


            toProcess.entries.sortedBy { it.key.addedOn }.forEach { (post, images) ->
                images.map { image ->
                    log.debug("Enqueuing a job for ${post.url}")
                    ImageQueueElement(
                        image.id, image.postEntityId, image.host
                    )
                }.also { queueManager.addPending(it) }
            }

            downloadManagerCondition.signal()
        }
    }

    private fun stopAll() {
        downloadManagerLock.withLock {
            queueManager.clearPending()
            queueManager.clearRunning()
            dataAccessService.findAllNonCompletedPostEntityIds().forEach {
                dataAccessService.stopImagesByPostEntityIdAndIsNotCompleted(it)
                dataAccessService.finishPost(it)
            }
        }
    }

    private fun stopInternal(postEntityIds: List<Long>) {
        downloadManagerLock.withLock {
            postEntityIds.forEach {
                queueManager.clearPending(it)
                queueManager.clearRunning(it)
                dataAccessService.stopImagesByPostEntityIdAndIsNotCompleted(it)
                dataAccessService.finishPost(it)
            }
        }
    }

    private fun canRun(hosts: Set<Byte>): Map<Byte, Int> {
        val runningSnapshot = queueManager.runningTotalCount().toMutableMap()
        return hosts.associateWith {
            val totalRunning = runningSnapshot.values.sum()
            val canRunGlobally =
                if (settingsService.settings.connectionSettings.maxGlobalConcurrent == 0) maxPoolSize - totalRunning else settingsService.settings.connectionSettings.maxGlobalConcurrent - totalRunning
            val canRunPerHost =
                settingsService.settings.connectionSettings.maxConcurrentPerHost - (runningSnapshot[it] ?: 0)
            val min = min(canRunGlobally, canRunPerHost)
            val actual = if (min < 0) 0 else min
            runningSnapshot[it] = (runningSnapshot[it] ?: 0) + actual
            actual
        }
    }

    private fun getCandidates(): Map<Byte, List<ImageQueueElement>> {
        return queueManager.pending().groupBy { it.host }
    }
}