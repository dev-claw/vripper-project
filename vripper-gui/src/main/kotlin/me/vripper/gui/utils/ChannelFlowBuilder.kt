package me.vripper.gui.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import me.vripper.gui.event.GuiEventBus

object ChannelFlowBuilder {

    fun <T> build(localFlow: () -> Flow<T>, remoteFlow: () -> Flow<T>, source: String = "remote"): Flow<T> {
        return channelFlow {
            var job: Job? = null

            fun collect(localSession: Boolean) {
                job = if (localSession) {
                    launch {
                        localFlow().cancellable().collect { if (isActive) send(it) }
                    }
                } else {
                    launch {
                        try {
                            remoteFlow().cancellable().collect { if (isActive) send(it) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            GuiEventBus.publishEvent(GuiEventBus.RemoteError(source, t.message ?: t.toString()))
                        }
                    }
                }
            }

            launch {
                GuiEventBus
                    .events
                    .collect {
                        if (job != null && job.isActive) {
                            job.cancelAndJoin()
                        }
                        when (it) {
                            GuiEventBus.LocalSession -> collect(true)
                            GuiEventBus.RemoteSession -> collect(false)
                            else -> {}
                        }
                    }
            }
        }
    }
}
