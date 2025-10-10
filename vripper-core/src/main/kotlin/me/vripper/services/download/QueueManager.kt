package me.vripper.services.download

import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import me.vripper.entities.Status
import me.vripper.event.ErrorCountEvent
import me.vripper.event.EventBus
import me.vripper.event.QueueStateEvent
import me.vripper.host.Host
import me.vripper.model.ErrorCount
import me.vripper.model.QueueState
import me.vripper.model.Rank
import me.vripper.services.DataAccessService
import me.vripper.services.RetryPolicyService
import me.vripper.services.SettingsService
import me.vripper.services.download.SharedLock.downloadManagerCondition
import me.vripper.services.download.SharedLock.downloadManagerLock
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.downloadRunner
import java.util.*
import kotlin.concurrent.withLock

internal class QueueManager(
    private val dataAccessService: DataAccessService,
    private val hosts: List<Host>,
    private val settingsService: SettingsService,
    private val retryPolicyService: RetryPolicyService,
    private val eventBus: EventBus
) {
    private val log by LoggerDelegate()
    private val running: MutableList<ImageDownloadRunnable> = mutableListOf()
    private val pending: MutableList<MutableList<ImageQueueElement>> = mutableListOf()

    fun addPending(imageQueueElementList: List<ImageQueueElement>) {
        pending.add(imageQueueElementList.toMutableList())
        reportQueueState()
    }

    fun clearPending(postEntityId: Long? = null) {
        val toProcess = if (postEntityId != null) pending.flatten()
            .filter { it.postEntityId == postEntityId } else pending.flatten()
        pending.forEach { it.removeAll(toProcess) }
        pending.removeIf { it.isEmpty() }
        reportQueueState()
    }

    fun runningTotalCount(): Map<Byte, Int> {
        return hosts.associate { host ->
            Pair(
                host.hostId, running.count { it.context.imageEntity.host == host.hostId })
        }
    }

    fun clearRunning(postEntityId: Long? = null) {
        val toProcess =
            if (postEntityId != null) running.filter { it.context.imageEntity.postEntityId == postEntityId } else running
        toProcess.forEach { it.stop() }
        while (toProcess.count { !it.completed } > 0) {
            Thread.sleep(100)
        }
        running.removeAll(toProcess)
    }

    fun clearRunningRunnable(imageId: Long) {
        running.removeIf { it.context.imageEntity.id == imageId }
    }

    fun accept(accepted: List<ImageQueueElement>) {
        pending.forEach {
            it.removeAll(accepted)
        }
        pending.removeIf { it.isEmpty() }
        accepted.map {
            ImageDownloadRunnable(
                dataAccessService.findImageById(it.imageEntityId).orElseThrow(), settingsService.settings.copy()
            )
        }.forEach {
            launch(it)
            running.add(it)
        }
        reportQueueState()
    }

    fun pending(): List<ImageQueueElement> {
        return pending.flatten()
    }

    fun isPending(postEntityId: Long): Boolean {
        return pending.flatten().any { it.postEntityId == postEntityId }
    }

    fun isRunning(postEntityId: Long): Boolean {
        return running.any { it.context.imageEntity.postEntityId == postEntityId }
    }

    fun move(postEntityId: Long, position: MovePosition) {
        downloadManagerLock.withLock {
            val index = pending.indexOfFirst { it.first().postEntityId == postEntityId }
            when (position) {
                MovePosition.UP -> {
                    val newIndex = if (index > 0) index - 1 else index
                    Collections.swap(pending, index, newIndex)
                }

                MovePosition.DOWN -> {
                    val newIndex = if (index < pending.size - 1) index + 1 else index
                    Collections.swap(pending, index, newIndex)
                }

                MovePosition.TOP -> {
                    if (index > 0) {
                        val element = pending.removeAt(index)
                        pending.addFirst(element)
                    }
                }

                MovePosition.BOTTOM -> {
                    if (index < pending.size - 1) {
                        val element = pending.removeAt(pending.size - 1)
                        pending.addLast(element)
                    }
                }
            }
        }
        reportQueueState()
    }

    fun getQueueState(): QueueState {
        val ranks = downloadManagerLock.withLock {
            pending.mapIndexed { index, elements -> Rank(elements.first().postEntityId, index + 1L) }
        }
        return QueueState(running.size, pending().size, ranks)
    }

    private fun launch(runnable: ImageDownloadRunnable) {
        log.debug("Scheduling a job for ${runnable.context.imageEntity.url}")
        reportQueueState()
        Failsafe.with<Any, RetryPolicy<Any>>(retryPolicyService.buildRetryPolicy("Failed to download ${runnable.context.imageEntity.url}: "))
            .with(downloadRunner).onFailure {
                log.error(
                    "Failed to download ${runnable.context.imageEntity.url} after ${it.attemptCount} tries",
                    it.exception
                )
                val image = runnable.context.imageEntity
                image.status = Status.ERROR
                dataAccessService.updateImage(image)
            }.onComplete {
                afterJobFinish(runnable)
                reportQueueState()
                eventBus.publishEvent(ErrorCountEvent(ErrorCount(dataAccessService.countImagesInError())))
                log.debug(
                    "Finished downloading ${runnable.context.imageEntity.url}"
                )
            }.runAsync(runnable)
    }

    private fun afterJobFinish(imageDownloadRunnable: ImageDownloadRunnable) {
        downloadManagerLock.withLock {
            val image = imageDownloadRunnable.context.imageEntity
            clearRunningRunnable(imageDownloadRunnable.context.imageEntity.id)
            if (!isPending(image.postEntityId) && !isRunning(image.postEntityId) && !imageDownloadRunnable.stopped) {
                dataAccessService.finishPost(image.postEntityId, true)
            }
            downloadManagerCondition.signal()
        }
    }

    private fun reportQueueState() {
        val queueState = getQueueState()
        eventBus.publishEvent(QueueStateEvent(queueState))
    }
}