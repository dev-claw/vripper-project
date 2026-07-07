package me.vripper.gui.controller

import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.GuiErrorReporter
import tornadofx.Controller

class StatusBarController : Controller() {
    suspend fun loggedInUser(): String {
        return GuiErrorReporter.call("status.user") { currentAppEndpointService().loggedInUser() }
            .getOrDefault("")
    }

    fun connectionState(): String {
        return runCatching { currentAppEndpointService().connectionState() }.getOrElse { "" }
    }

    suspend fun getVersion(): String {
        return GuiErrorReporter.call("status.version") { currentAppEndpointService().getVersion() }
            .getOrDefault("")
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