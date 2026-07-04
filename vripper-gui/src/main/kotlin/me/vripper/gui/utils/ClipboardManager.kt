package me.vripper.gui.utils

import javafx.scene.input.Clipboard
import kotlinx.coroutines.*
import me.vripper.gui.event.GuiEventBus
import me.vripper.model.Settings
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import tornadofx.runLater

object ClipboardManager : KoinComponent {
    private val logger by LoggerDelegate()
    private var current: String? = null
    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val systemClipboard = Clipboard.getSystemClipboard()
    val localAppEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))
    val remoteAppEndpointService: IAppEndpointService by inject(named("remoteAppEndpointService"))
    var initialized = false

    @Synchronized
    fun init() {
        if (initialized) {
            return
        }
        runBlocking {
            val updateSettings = ChannelFlowBuilder.build(
                localAppEndpointService::onUpdateSettings,
                remoteAppEndpointService::onUpdateSettings
            )
            updateSettings.let { flow ->
                coroutineScope.launch {
                    flow.collect {
                        update(it)
                    }
                }
            }
            coroutineScope.launch {
                GuiEventBus.events.collect { event ->
                    when (event) {
                        GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                            logger.info("Clipboard manager initialized")
                            val result = runCatching { AppEndpointManager.currentAppEndpointService().getSettings() }
                            if (result.isSuccess) {
                                update(result.getOrNull()!!)
                            }
                        }

                        else -> {}
                    }
                }
            }
            initialized = true
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
                        if (systemClipboard.hasString()) {
                            value = systemClipboard.string
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