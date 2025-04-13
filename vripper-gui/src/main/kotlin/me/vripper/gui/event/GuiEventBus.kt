package me.vripper.gui.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GuiEventBus {

    private val _events = MutableSharedFlow<Any>()
    val events = _events.asSharedFlow()

    suspend fun publishEvent(event: Any) {
        _events.emit(event)
    }

    data class ApplicationInitialized(val args: List<String>)
    object ChangingSession
    object LocalSession
    object RemoteSession
}
