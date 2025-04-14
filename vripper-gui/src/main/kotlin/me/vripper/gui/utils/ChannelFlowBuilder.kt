package me.vripper.gui.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.retryWhen
import me.vripper.gui.event.GuiEventBus

object ChannelFlowBuilder {

    fun <T> build(localFlow: () -> Flow<T>, remoteFlow: () -> Flow<T>): Flow<T> {
        return channelFlow {
            var job: Job? = null

            fun collect(localSession: Boolean) {
                job = if (localSession) {
                    launch {
                        localFlow().cancellable().collect { if (isActive) send(it) }
                    }
                } else {
                    launch {
                        remoteFlow().cancellable().retryWhen { _, _ ->
                            delay(1000)
                            true
                        }.collect { if (isActive) send(it) }
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

    fun <T> toFlow(source: suspend () -> List<T>): Flow<T> {
        return channelFlow {
            source().forEach { if (isActive) send(it) }
        }.retryWhen { _, _ -> delay(1000); true }
    }
}
