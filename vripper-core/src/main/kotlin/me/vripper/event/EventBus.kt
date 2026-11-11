package me.vripper.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object EventBus {

    private val _events = MutableSharedFlow<Any>(0, BUFFER_CAPACITY, BufferOverflow.DROP_OLDEST)
    val events = _events.asSharedFlow()

    fun publishEvent(event: Any) {
        _events.tryEmit(event)
    }

    private const val BUFFER_CAPACITY = 256
}
