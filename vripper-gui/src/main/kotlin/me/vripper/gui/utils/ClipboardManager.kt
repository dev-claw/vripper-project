package me.vripper.gui.utils

import javafx.scene.input.Clipboard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.retryWhen
import me.vripper.gui.event.GuiEventBus
import me.vripper.model.Settings
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import tornadofx.runLater

object ClipboardManager : KoinComponent {
    private val logger by LoggerDelegate()
    private var current: String? = null
    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var settingsUpdateJob: Job? = null

    fun init() {
        coroutineScope.launch {
            GuiEventBus.events.collect { event ->
                when (event) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        logger.info("Clipboard manager initialized")
                        while (isActive) {
                            val result = runCatching { AppEndpointManager.currentAppEndpointService().getSettings() }
                            if (result.isSuccess) {
                                update(result.getOrNull()!!)
                                break
                            }
                            delay(1000)
                        }
                        settingsUpdateJob = launch {
                            AppEndpointManager.currentAppEndpointService().onUpdateSettings()
                                .retryWhen { _, _ -> delay(1000); true }.collect {
                                    update(it)
                                }
                        }
                    }
                    GuiEventBus.ChangingSession -> {
                        settingsUpdateJob?.cancelAndJoin()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun update(settings: Settings) {
        pollJob?.cancel()
        if (settings.systemSettings.enableClipboardMonitoring) {
            logger.info("Polling clipboard every ${settings.systemSettings.clipboardPollingRate}ms")
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
                        runCatching { AppEndpointManager.currentAppEndpointService().scanLinks(value) }
                    }
                    delay(settings.systemSettings.clipboardPollingRate.toLong())
                }
            }
        } else {
            logger.info("Clipboard monitoring deactivated")
            current = null
        }
    }
}