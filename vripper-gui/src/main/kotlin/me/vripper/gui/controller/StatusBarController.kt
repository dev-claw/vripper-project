package me.vripper.gui.controller

import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import tornadofx.Controller

class StatusBarController : Controller() {
    suspend fun loggedInUser(): String {
        return currentAppEndpointService().loggedInUser()
    }

    fun connectionState(): String {
        return currentAppEndpointService().connectionState()
    }

    suspend fun getVersion(): String {
        return currentAppEndpointService().getVersion()
    }

    val vgUserUpdate = ChannelFlowBuilder.build(
        localAppEndpointService::onVGUserUpdate,
        remoteAppEndpointService::onVGUserUpdate,
    )

    val tasksRunning = ChannelFlowBuilder.build(
        localAppEndpointService::onTasksRunning,
        remoteAppEndpointService::onTasksRunning,
    )

    val downloadSpeed = ChannelFlowBuilder.build(
        localAppEndpointService::onDownloadSpeed,
        remoteAppEndpointService::onDownloadSpeed,
    )

    val queueStateUpdate = ChannelFlowBuilder.build(
        localAppEndpointService::onQueueStateUpdate,
        remoteAppEndpointService::onQueueStateUpdate,
    )

    val errorCountUpdate = ChannelFlowBuilder.build(
        localAppEndpointService::onErrorCountUpdate,
        remoteAppEndpointService::onErrorCountUpdate
    )
}