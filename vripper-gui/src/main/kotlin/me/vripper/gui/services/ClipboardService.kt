package me.vripper.gui.services

import io.grpc.StatusException
import javafx.scene.input.Clipboard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.gui.event.GuiEventBus
import me.vripper.model.Settings
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.LoggerDelegate
import tornadofx.Controller
import tornadofx.runLater

class ClipboardService : Controller() {
    private val logger by LoggerDelegate()
    private var current: String? = null
    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var subscribeJob: Job? = null

    fun init(appEndpointService: IAppEndpointService) {
        subscribeJob?.cancel()
        subscribeJob = coroutineScope.launch {
            appEndpointService.onUpdateSettings().catch {
                if (it !is StatusException) {
                    logger.error("gRPC error", it)
                }
            }.collect {
                run(it, appEndpointService)
            }
        }
        coroutineScope.launch {
            GuiEventBus.events.filterIsInstance(GuiEventBus.ChangingSession::class).collect {
                subscribeJob?.cancel()
            }
        }
        runBlocking {
            run(appEndpointService.getSettings(), appEndpointService)
        }
    }

    private fun run(settings: Settings, appEndpointService: IAppEndpointService) {
        pollJob?.cancel()
        if (settings.systemSettings.enableClipboardMonitoring) {
            pollJob = coroutineScope.launch {
                var value: String? = null
                while (isActive) {
                    runLater {
                        val clipboard = Clipboard.getSystemClipboard()
                        if (clipboard.hasString()) {
                            value = clipboard.string
                        }
                    }
                    if (!value.isNullOrBlank() && value != current) {
                        current = value
                        appEndpointService.scanLinks(value)
                    }
                    delay(settings.systemSettings.clipboardPollingRate.toLong())
                }
            }
        } else {
            current = null
        }
    }
}