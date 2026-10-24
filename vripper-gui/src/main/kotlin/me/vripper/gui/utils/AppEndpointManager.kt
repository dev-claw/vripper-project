package me.vripper.gui.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.vripper.gui.event.GUIEvent
import me.vripper.gui.event.GuiEventBus
import me.vripper.services.IAppEndpointService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

object AppEndpointManager : KoinComponent {

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val localAppEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))
    val remoteAppEndpointService: IAppEndpointService by inject(named("remoteAppEndpointService"))

    @Volatile
    private var current: GUIEvent = GuiEventBus.Undefined

    private val _sessionUpdates = MutableSharedFlow<GUIEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sessionUpdates: Flow<GUIEvent> = _sessionUpdates.asSharedFlow()

    fun set(event: GUIEvent) {
        this.current = event
        _sessionUpdates.tryEmit(event)
    }

    fun currentAppEndpointService(): IAppEndpointService {
        return when (current) {
            GuiEventBus.LocalSession -> localAppEndpointService
            GuiEventBus.RemoteSession -> remoteAppEndpointService
            else -> throw IllegalStateException("Unknown current state: $current")
        }
    }

    fun currentAppState(): GUIEvent {
        return this.current
    }
}