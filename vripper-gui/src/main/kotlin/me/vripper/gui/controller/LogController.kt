package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.gui.model.LogModel
import me.vripper.model.LogEntry
import me.vripper.services.IAppEndpointService
import tornadofx.Controller

class LogController : Controller() {
    lateinit var appEndpointService: IAppEndpointService

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

    fun onNewLog() = appEndpointService.onNewLog().map(::mapper)

    suspend fun initLogger() {
        appEndpointService.initLogger()
    }

    fun onUpdateSettings() =
        appEndpointService.onUpdateSettings()

}