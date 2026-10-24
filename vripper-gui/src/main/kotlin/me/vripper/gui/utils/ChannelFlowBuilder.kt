package me.vripper.gui.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.vripper.gui.event.GuiEventBus
import me.vripper.utilities.LoggerDelegate

object ChannelFlowBuilder {

    private const val INITIAL_RETRY_DELAY_MS = 1_000L
    private const val MAX_RETRY_DELAY_MS = 30_000L
    private val log by LoggerDelegate()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> build(localFlow: () -> Flow<T>, remoteFlow: () -> Flow<T>, source: String = "remote"): Flow<T> =
        AppEndpointManager.sessionUpdates
            .flatMapLatest { session ->
                when (session) {
                    GuiEventBus.LocalSession -> localFlowWithErrorReporting(localFlow, source)
                    GuiEventBus.RemoteSession -> remoteFlowWithRetry(remoteFlow, source)
                    else -> emptyFlow()
                }
            }

    private fun <T> localFlowWithErrorReporting(localFlow: () -> Flow<T>, source: String): Flow<T> = flow {
        try {
            localFlow().cancellable().collect { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (!currentCoroutineContext().isActive || AppEndpointManager.currentAppState() != GuiEventBus.LocalSession) {
                return@flow
            }
            log.error("Local flow '$source' failed", t)
            GuiErrorReporter.report("local:$source", t)
        }
    }

    private fun <T> remoteFlowWithRetry(remoteFlow: () -> Flow<T>, source: String): Flow<T> = flow {
        var retryDelay = INITIAL_RETRY_DELAY_MS
        var failureReported = false

        while (true) {
            try {
                remoteFlow().cancellable().collect { value ->
                    failureReported = false
                    retryDelay = INITIAL_RETRY_DELAY_MS
                    emit(value)
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (!currentCoroutineContext().isActive || AppEndpointManager.currentAppState() != GuiEventBus.RemoteSession) {
                    return@flow
                }
                log.error("Remote flow '$source' failed, retrying in ${retryDelay}ms", t)
                if (!failureReported) {
                    GuiErrorReporter.report(source, t)
                    failureReported = true
                }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }
}
