package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.gui.model.LogModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.model.LogEntry
import tornadofx.Controller

class LogController : Controller() {

    val newLogs = ChannelFlowBuilder.build(
        { localAppEndpointService.onNewLog().map(::mapper) },
        { remoteAppEndpointService.onNewLog().map(::mapper) }
    )

    val updateSettings =
        ChannelFlowBuilder.build(
            localAppEndpointService::onUpdateSettings,
            remoteAppEndpointService::onUpdateSettings
        )

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