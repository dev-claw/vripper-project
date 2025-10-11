package me.vripper.gui.components.views

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import javafx.geometry.Pos
import kotlinx.coroutines.*
import me.vripper.gui.controller.StatusBarController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.utilities.formatSI
import tornadofx.*

class StatusBarView : View("Status bar") {
    private val widgetsController: WidgetsController by inject()
    private val statusBarController: StatusBarController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remoteText = SimpleStringProperty()
    private val loggedUser = SimpleStringProperty()
    private val tasksRunning = SimpleBooleanProperty(false)
    private val downloadSpeed = SimpleStringProperty(0L.formatSI())
    private val running = SimpleIntegerProperty(0)
    private val pending = SimpleIntegerProperty(0)
    private val error = SimpleIntegerProperty(0)

    init {
        coroutineScope.launch {
            val jobs = mutableListOf<Job>()
            GuiEventBus.events.collect { event ->
                when (event) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        while (isActive) {
                            val result = runCatching { statusBarController.loggedInUser() }
                            if (result.isSuccess) {
                                runLater {
                                    loggedUser.set(result.getOrNull())
                                }
                                break
                            }
                            delay(1000)
                        }
                        launch {
                            statusBarController.vgUserUpdate.collect {
                                runLater {
                                    loggedUser.set(it)
                                }
                            }
                        }.also { jobs.add(it) }
                        launch {
                            statusBarController.tasksRunning.collect {
                                runLater {
                                    tasksRunning.set(it)
                                }
                            }
                        }.also { jobs.add(it) }
                        launch {
                            statusBarController.downloadSpeed.collect {
                                runLater {
                                    downloadSpeed.set(it.speed.formatSI())
                                }
                            }
                        }.also { jobs.add(it) }
                        launch {
                            statusBarController.queueStateUpdate.collect {
                                runLater {
                                    running.set(it.running)
                                    pending.set(it.remaining)
                                }
                            }
                        }.also { jobs.add(it) }
                        launch {
                            statusBarController.errorCountUpdate.collect {
                                runLater {
                                    error.set(it.count)
                                }
                            }
                        }.also { jobs.add(it) }
                    }

                    GuiEventBus.ChangingSession -> {
                        jobs.forEach { it.cancelAndJoin() }
                        runLater {
                            loggedUser.set("")
                            tasksRunning.set(false)
                            downloadSpeed.set(0L.formatSI())
                            running.set(0)
                            pending.set(0)
                            error.set(0)
                        }
                    }

                    else -> {}
                }
            }
        }

        coroutineScope.launch {
            while (isActive) {
                val text = when (statusBarController.connectionState()) {
                    "CONNECTING" -> "Connecting to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    "READY" -> "Connected to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port} ${statusBarController.getVersion()}"
                    "TRANSIENT_FAILURE" -> "Failing to connect to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    "IDLE" -> "Idle connection to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    "SHUTDOWN" -> "Connection shutdown to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    else -> ""
                }

                runLater {
                    remoteText.set(text)
                }
                delay(1000)
            }
        }
    }

    override val root = borderpane {
        this.id = "statusbar"
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
