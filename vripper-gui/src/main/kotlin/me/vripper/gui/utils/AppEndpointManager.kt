package me.vripper.gui.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private lateinit var current: GUIEvent

    fun set(event: GUIEvent) {
        this.current = event
    }

    fun currentAppEndpointService(): IAppEndpointService {
        return when (current) {
            GuiEventBus.LocalSession -> localAppEndpointService
            GuiEventBus.RemoteSession -> remoteAppEndpointService
            else -> throw IllegalStateException("Unknown current state: $current")
        }
    }
}