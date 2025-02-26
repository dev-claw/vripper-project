package me.vripper.gui.components.views

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import javafx.geometry.Pos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.utils.ActiveUICoroutines
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.formatSI
import tornadofx.*

class StatusBarView : View("Status bar") {
    private val logger by LoggerDelegate()
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val grpcEndpointService: IAppEndpointService by di("remoteAppEndpointService")
    private val localEndpointService: IAppEndpointService by di("localAppEndpointService")
    private val remoteText = SimpleStringProperty()
    private val loggedUser = SimpleStringProperty()
    private val tasksRunning = SimpleBooleanProperty(false)
    private val downloadSpeed = SimpleStringProperty(0L.formatSI())
    private val running = SimpleIntegerProperty(0)
    private val pending = SimpleIntegerProperty(0)
    private val error = SimpleIntegerProperty(0)

    init {
        coroutineScope.launch {
            GuiEventBus.events.collect { event ->
                runLater {
                    tasksRunning.set(false)
                    downloadSpeed.set(0L.formatSI())
                    running.set(0)
                    pending.set(0)
                    error.set(0)
                }

                when (event) {
                    is GuiEventBus.LocalSession -> {
                        connect(localEndpointService)
                    }

                    is GuiEventBus.RemoteSession -> {
                        connect(grpcEndpointService)
                    }

                    is GuiEventBus.RemoteSessionFailure -> {
                        runLater {
                            remoteText.set("Unable to connect to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}")
                        }
                    }

                    is GuiEventBus.ChangingSession -> {
                        ActiveUICoroutines.statusBar.forEach { it.cancelAndJoin() }
                        ActiveUICoroutines.statusBar.clear()
                        runLater {
                            remoteText.set("Connecting to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}")
                        }
                    }
                }
            }
        }
    }

    private fun connect(endpointService: IAppEndpointService) {
        coroutineScope.launch {
            val user = async { endpointService.loggedInUser() }.await()
            runLater {
                loggedUser.set(user)
            }
        }

        coroutineScope.launch {
            endpointService.onVGUserUpdate().catch {
                logger.error("gRPC error", it)
                currentCoroutineContext().cancel(null)
            }.collect {
                runLater {
                    loggedUser.set(it)
                }
            }
        }.also { ActiveUICoroutines.statusBar.add(it) }

        coroutineScope.launch {
            endpointService.onTasksRunning().catch {
                logger.error("gRPC error", it)
                currentCoroutineContext().cancel(null)
            }.collect {
                runLater {
                    tasksRunning.set(it)
                }
            }
        }.also { ActiveUICoroutines.statusBar.add(it) }

        coroutineScope.launch {
            endpointService.onDownloadSpeed().catch {
                logger.error("gRPC error", it)
                currentCoroutineContext().cancel(null)
            }.collect {
                runLater {
                    downloadSpeed.set(it.speed.formatSI())
                }
            }
        }.also { ActiveUICoroutines.statusBar.add(it) }

        coroutineScope.launch {
            endpointService.onQueueStateUpdate().catch {
                logger.error("gRPC error", it)
                currentCoroutineContext().cancel(null)
            }.collect {
                runLater {
                    running.set(it.running)
                    pending.set(it.remaining)
                }
            }
        }.also { ActiveUICoroutines.statusBar.add(it) }

        coroutineScope.launch {
            endpointService.onErrorCountUpdate().catch {
                logger.error("gRPC error", it)
                currentCoroutineContext().cancel(null)
            }.collect {
                runLater {
                    error.set(it.count)
                }
            }
        }.also { ActiveUICoroutines.statusBar.add(it) }
        if (widgetsController.currentSettings.localSession) {
            runLater {
                remoteText.set("")
            }
        } else {
            coroutineScope.launch {
                if (grpcEndpointService.ready()) {
                    val version = grpcEndpointService.getVersion()
                    runLater {
                        remoteText.set("Connected to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port} v$version")
                    }
                } else {
                    runLater {
                        remoteText.set("Unable to connect to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}")
                    }
                }
            }
        }
    }

    override val root = borderpane {
        id = "statusbar"
        left {
            hbox {
                label(remoteText) {
                    managedProperty().bind(visibleProperty())
                    visibleWhen { widgetsController.currentSettings.localSessionProperty.not() }
                }
                separator(Orientation.VERTICAL) {
                    managedProperty().bind(visibleProperty())
                    visibleWhen {
                        widgetsController.currentSettings.localSessionProperty.not().and(remoteText.isNotBlank())
                    }
                }
                label(loggedUser.map { "Logged in as: $it" }) {
                    visibleWhen(loggedUser.isNotBlank())
                }
            }
        }
        right {
            padding = insets(right = 5, left = 5, top = 3, bottom = 3)
            hbox {
                spacing = 3.0
                progressbar {
                    visibleWhen(tasksRunning)
                }
                separator(Orientation.VERTICAL) {
                    visibleWhen(tasksRunning)
                }
                label(downloadSpeed.map { "$it/s" })
                separator(Orientation.VERTICAL)
                label("Downloading")
                label(running.asString())
                separator(Orientation.VERTICAL)
                label("Pending")
                label(pending.asString())
                separator(Orientation.VERTICAL)
                label("Error")
                label(error.asString())
                alignment = Pos.CENTER_RIGHT
            }
        }
    }
}
