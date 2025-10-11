package me.vripper.gui.controller

import kotlinx.coroutines.flow.cancellable
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
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

    val vgUserUpdate = currentAppEndpointService().onVGUserUpdate().cancellable()

    val tasksRunning = currentAppEndpointService().onTasksRunning().cancellable()

    val downloadSpeed = currentAppEndpointService().onDownloadSpeed().cancellable()

    val queueStateUpdate = currentAppEndpointService().onQueueStateUpdate().cancellable()

    val errorCountUpdate = currentAppEndpointService().onErrorCountUpdate().cancellable()
}