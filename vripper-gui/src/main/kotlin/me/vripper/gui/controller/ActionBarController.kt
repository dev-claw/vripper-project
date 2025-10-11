package me.vripper.gui.controller

import kotlinx.coroutines.flow.cancellable
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import tornadofx.Controller

class ActionBarController : Controller() {

    val onQueueStateUpdate =
        currentAppEndpointService().onQueueStateUpdate().cancellable()
}