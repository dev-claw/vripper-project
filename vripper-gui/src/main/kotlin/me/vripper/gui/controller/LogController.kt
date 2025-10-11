package me.vripper.gui.controller

import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.map
import me.vripper.gui.model.LogModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.model.LogEntry
import tornadofx.Controller

class LogController : Controller() {

    val newLogs = currentAppEndpointService().onNewLog().map(::mapper).cancellable()

    val updateSettings = currentAppEndpointService().onUpdateSettings().cancellable()

    private fun mapper(it: LogEntry): LogModel {
        return LogModel(
            it.sequence,
            it.timestamp,
            it.threadName,
            it.loggerName,
            it.levelString,
            it.formattedMessage,
            it.throwable,
        )
    }

    suspend fun initLogger() {
        currentAppEndpointService().initLogger()
    }

    suspend fun getMaxEventLog(): Int {
        return currentAppEndpointService().getSettings().systemSettings.maxEventLog
    }
}