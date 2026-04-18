package me.vripper.services

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.event.DownloadSpeedEvent
import me.vripper.event.EventBus
import me.vripper.event.QueueStateEvent
import me.vripper.model.DownloadSpeed
import me.vripper.utilities.LoggerDelegate
import java.util.concurrent.atomic.AtomicLong

internal class DownloadSpeedService(
    private val eventBus: EventBus,
) {

    companion object {
        const val DOWNLOAD_POLL_RATE = 2500
    }

    private val log by LoggerDelegate()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bytesCount = AtomicLong(0)
    private var job: Job? = null
    private var queueStateUpdateJob: Job? = null

    fun init() {
        queueStateUpdateJob?.cancel()
        queueStateUpdateJob = coroutineScope.launch {
            eventBus.events.filterIsInstance(QueueStateEvent::class).collect {
                if (it.queueState.running + it.queueState.remaining > 0) {
                    if (job == null || job?.isActive == false) {
                        job = coroutineScope.launch {
                            eventBus.publishEvent(DownloadSpeedEvent(DownloadSpeed(0L)))
                            while (isActive) {
                                delay(DOWNLOAD_POLL_RATE.toLong())
                                val newValue = bytesCount.getAndSet(0)
                                val speed = DownloadSpeed(((newValue * 1000) / DOWNLOAD_POLL_RATE))
                                log.debug(
                                    "[{}] Publishing event: DownloadSpeedEvent({})",
                                    System.currentTimeMillis(),
                                    speed
                                )
                                eventBus.publishEvent(DownloadSpeedEvent(speed))
                            }
                        }
                    }
                } else {
                    job?.cancel()
                    coroutineScope.launch {
                        delay(DOWNLOAD_POLL_RATE + 500L)
                        log.debug("[{}] Publishing event: DownloadSpeedEvent(0)", System.currentTimeMillis())
                        eventBus.publishEvent(DownloadSpeedEvent(DownloadSpeed(0L)))
                    }
                }
            }
        }
    }

    fun halt() {
        queueStateUpdateJob?.cancel()
        job?.cancel()
    }

    fun reportDownloadedBytes(count: Long) {
        bytesCount.addAndGet(count)
    }
}
