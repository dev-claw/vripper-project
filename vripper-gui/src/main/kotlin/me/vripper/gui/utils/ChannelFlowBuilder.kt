package me.vripper.gui.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive

object ChannelFlowBuilder {

    fun <T> toFlow(source: suspend () -> List<T>): Flow<T> {
        return channelFlow {
            source().forEach { if (isActive) send(it) }
        }.retryWhen { _, _ -> delay(1000); true }
    }
}
