package me.vripper.gui.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GuiEventBus {

    private val _events = MutableSharedFlow<GUIEvent>(0, Int.MAX_VALUE, BufferOverflow.DROP_OLDEST)
    val events = _events.asSharedFlow()

    suspend fun publishEvent(event: GUIEvent) {
        _events.emit(event)
    }

    data class ApplicationInitialized(val args: List<String>) : GUIEvent
    object ChangingSession : GUIEvent
    object LocalSession : GUIEvent
    object RemoteSession : GUIEvent
}

sealed interface GUIEvent