package me.vripper.gui.controller

import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import tornadofx.Controller

class ActionBarController : Controller() {

    val onQueueStateUpdate = ChannelFlowBuilder.build(
        localAppEndpointService::onQueueStateUpdate,
        remoteAppEndpointService::onQueueStateUpdate
    )
}