package me.vripper.gui.components.views

import io.grpc.ConnectivityState
import io.grpc.StatusException
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import javafx.geometry.Pos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.services.GrpcEndpointService
import me.vripper.gui.utils.ActiveUICoroutines
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.formatSI
import tornadofx.*

class StatusBarView : View("Status bar") {
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val grpcEndpointService: GrpcEndpointService by di("remoteAppEndpointService")
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

                    is GuiEventBus.ChangingSession -> {
                        ActiveUICoroutines.cancelStatusBar()
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

        connectToVGUserUpdate(endpointService)
        connectToTasksRunning(endpointService)
        connectToDownloadSpeed(endpointService)
        connectToQueueStateUpdate(endpointService)
        connectToErrorCountUpdate(endpointService)

        if (widgetsController.currentSettings.localSession) {
            runLater {
                remoteText.set("")
            }
        } else {
            coroutineScope.launch {
                while (isActive) {
                    val text = when (grpcEndpointService.connectionState()) {
                        ConnectivityState.CONNECTING -> "Connecting to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                        ConnectivityState.READY -> "Connected to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port} ${grpcEndpointService.getVersion()}"
                        ConnectivityState.TRANSIENT_FAILURE -> "Failing to connect to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                        ConnectivityState.IDLE -> "Idle connection to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                        ConnectivityState.SHUTDOWN -> "Connection shutdown to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    }
                    runLater {
                        remoteText.set(text)
                    }
                    delay(1000)
                }
            }
        }
    }

    private fun connectToErrorCountUpdate(endpointService: IAppEndpointService) {
        coroutineScope.launch {
            endpointService.onErrorCountUpdate().catch {
                ActiveUICoroutines.removeFromStatusBar(currentCoroutineContext().job)

                if (it is StatusException) {
                    //reconnect
                    coroutineScope.launch {
                        delay(1000)
                        connectToErrorCountUpdate(endpointService)
                    }
                }
            }.collect {
                runLater {
                    error.set(it.count)
                }
            }
        }.also { runBlocking { ActiveUICoroutines.addToStatusBar(it) } }
    }

    private fun connectToQueueStateUpdate(endpointService: IAppEndpointService) {
        coroutineScope.launch {
            endpointService.onQueueStateUpdate().catch {
                ActiveUICoroutines.removeFromStatusBar(currentCoroutineContext().job)

                if (it is StatusException) {
                    //reconnect
                    coroutineScope.launch {
                        delay(1000)
                        connectToQueueStateUpdate(endpointService)
                    }
                }
            }.collect {
                runLater {
                    running.set(it.running)
                    pending.set(it.remaining)
                }
            }
        }.also { runBlocking { ActiveUICoroutines.addToStatusBar(it) } }
    }

    private fun connectToDownloadSpeed(endpointService: IAppEndpointService) {
        coroutineScope.launch {
            endpointService.onDownloadSpeed().catch {
                ActiveUICoroutines.removeFromStatusBar(currentCoroutineContext().job)

                if (it is StatusException) {
                    //reconnect
                    coroutineScope.launch {
                        delay(1000)
                        connectToDownloadSpeed(endpointService)
                    }
                }
            }.collect {
                runLater {
                    downloadSpeed.set(it.speed.formatSI())
                }
            }
        }.also { runBlocking { ActiveUICoroutines.addToStatusBar(it) } }
    }

    private fun connectToTasksRunning(endpointService: IAppEndpointService) {
        coroutineScope.launch {
            endpointService.onTasksRunning().catch {
                ActiveUICoroutines.removeFromStatusBar(currentCoroutineContext().job)

                if (it is StatusException) {
                    //reconnect
                    coroutineScope.launch {
                        delay(1000)
                        connectToTasksRunning(endpointService)
                    }
                }
            }.collect {
                runLater {
                    tasksRunning.set(it)
                }
            }
        }.also { runBlocking { ActiveUICoroutines.addToStatusBar(it) } }
    }

    private fun connectToVGUserUpdate(endpointService: IAppEndpointService) {
        coroutineScope.launch {
            endpointService.onVGUserUpdate().catch {
                ActiveUICoroutines.removeFromStatusBar(currentCoroutineContext().job)

                if (it is StatusException) {
                    //reconnect
                    coroutineScope.launch {
                        delay(1000)
                        connectToVGUserUpdate(endpointService)
                    }
                }
            }.collect {
                runLater {
                    loggedUser.set(it)
                }
            }
        }.also { runBlocking { ActiveUICoroutines.addToStatusBar(it) } }
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
