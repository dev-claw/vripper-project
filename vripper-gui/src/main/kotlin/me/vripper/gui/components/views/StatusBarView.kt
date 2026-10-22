package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import javafx.animation.PauseTransition
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.binding.IntegerExpression
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.OverrunStyle
import javafx.scene.control.ProgressBar
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.util.Duration
import kotlinx.coroutines.*
import me.vripper.gui.controller.StatusBarController
import me.vripper.gui.controller.TaskController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.utilities.formatSI
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class StatusBarView : View("Status bar") {

    private companion object {
        const val RUNNING_TASK_LINGER_SECONDS = 2.0
    }
    private val widgetsController: WidgetsController by inject()
    private val statusBarController: StatusBarController by inject()
    private val taskController: TaskController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remoteText = SimpleStringProperty()
    private val loggedUser = SimpleStringProperty()
    private val tasksRunning = SimpleBooleanProperty(false)
    private val showRunningTask = SimpleBooleanProperty(false)
    private val downloadSpeed = SimpleStringProperty(0L.formatSI())
    private val running = SimpleIntegerProperty(0)
    private val pending = SimpleIntegerProperty(0)
    private val error = SimpleIntegerProperty(0)
    private val connectionState = SimpleStringProperty("")
    private val runningTaskName = SimpleStringProperty("Preparing task…")
    private val runningTaskDetail = SimpleStringProperty("")
    private val connectionIcon = FontIcon.of(Feather.GLOBE).apply {
        styleClass.add("vripper-icon-statusbar")
    }
    private val hideRunningTask = PauseTransition(Duration.seconds(RUNNING_TASK_LINGER_SECONDS)).apply {
        setOnFinished {
            showRunningTask.set(false)
            runningTaskName.set("Preparing task…")
            runningTaskDetail.set("")
        }
    }

    @Volatile
    private var serverVersion = ""

    init {
        connectionIcon.styleClass.add(Styles.TEXT_MUTED)
        connectionState.addListener { _, _, state ->
            connectionIcon.styleClass.removeAll(
                Styles.SUCCESS,
                Styles.WARNING,
                Styles.DANGER,
                Styles.TEXT_MUTED,
            )
            connectionIcon.styleClass.add(
                when (state) {
                    "READY" -> Styles.SUCCESS
                    "CONNECTING" -> Styles.WARNING
                    "TRANSIENT_FAILURE" -> Styles.DANGER
                    else -> Styles.TEXT_MUTED
                }
            )
        }

        coroutineScope.launch {
            GuiEventBus.events.collect {
                when (it) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        serverVersion = ""
                        val result = runCatching { statusBarController.loggedInUser() }
                        if (result.isSuccess) {
                            runLater {
                                loggedUser.set(result.getOrNull())
                            }
                        } else {
                            runLater {
                                loggedUser.set(null)
                            }
                        }
                    }

                    else -> {}
                }
            }
        }

        statusBarController.vgUserUpdate.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        loggedUser.set(it)
                    }
                }
            }
        }

        statusBarController.tasksRunning.let { flow ->
            coroutineScope.launch {
                flow.collect { runningNow ->
                    runLater {
                        tasksRunning.set(runningNow)
                        if (runningNow) {
                            hideRunningTask.stop()
                            showRunningTask.set(true)
                        } else {
                            scheduleRunningTaskHide()
                        }
                    }
                }
            }
        }

        taskController.runningTask.let { flow ->
            coroutineScope.launch {
                flow.collect { task ->
                    runLater {
                        if (task != null) {
                            runningTaskName.set(
                                task.name.takeIf { it.isNotBlank() }
                                    ?: task.description.takeIf { it.isNotBlank() }
                                    ?: "Preparing task…"
                            )
                            runningTaskDetail.set(task.description)
                            hideRunningTask.stop()
                            showRunningTask.set(true)
                        } else if (!tasksRunning.get()) {
                            scheduleRunningTaskHide()
                        }
                    }
                }
            }
        }

        statusBarController.downloadSpeed.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        downloadSpeed.set(it.speed.formatSI())
                    }
                }
            }
        }

        statusBarController.queueStateUpdate.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        running.set(it.running)
                        pending.set(it.remaining)
                    }
                }
            }
        }

        statusBarController.errorCountUpdate.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        error.set(it.count)
                    }
                }
            }
        }

        coroutineScope.launch {
            var lastConnectionState = ""
            while (isActive) {
                val state = statusBarController.connectionState()
                // The version only changes when the endpoint changes; fetching
                // it on every status-bar tick meant one gRPC call per second.
                if (state == "READY" &&
                    (state != lastConnectionState || serverVersion.isEmpty())
                ) {
                    runCatching { statusBarController.getVersion() }
                        .onSuccess { serverVersion = it }
                }
                lastConnectionState = state
                val text = when (state) {
                    "CONNECTING" -> "Connecting to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    "READY" -> "Connected to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port} $serverVersion"
                    "TRANSIENT_FAILURE" -> "Failing to connect to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    "IDLE" -> "Idle connection to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    "SHUTDOWN" -> "Connection shutdown to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}"
                    else -> ""
                }

                runLater {
                    remoteText.set(text)
                    connectionState.set(state)
                }
                delay(1000)
            }
        }
    }

    override val root = borderpane {
        id = "statusbar"
        padding = Insets(7.0, 10.0, 7.0, 10.0)
        addClass(Styles.BG_SUBTLE)
        addClass(Styles.BORDER_SUBTLE)
        style = "-fx-border-width: 1 0 0 0;"

        left {
            hbox {
                spacing = 12.0
                alignment = Pos.CENTER_LEFT

                hbox {
                    spacing = 6.0
                    alignment = Pos.CENTER_LEFT
                    visibleWhen { widgetsController.currentSettings.localSessionProperty.not() }
                    managedProperty().bind(visibleProperty())
                    add(connectionIcon)
                    label(remoteText) {
                        addClass(Styles.TEXT_SMALL, Styles.TEXT_MUTED)
                        minWidth = 0.0
                        maxWidth = 520.0
                        textOverrun = OverrunStyle.ELLIPSIS
                    }
                }

                hbox {
                    spacing = 6.0
                    alignment = Pos.CENTER_LEFT
                    visibleWhen(loggedUser.isNotBlank())
                    managedProperty().bind(visibleProperty())
                    add(FontIcon.of(Feather.USER).apply {
                        styleClass.addAll("vripper-icon-statusbar", Styles.TEXT_MUTED)
                    })
                    label(loggedUser.map { "Logged in as $it" }) {
                        addClass(Styles.TEXT_SMALL, Styles.TEXT_MUTED)
                        minWidth = 0.0
                        maxWidth = 260.0
                        textOverrun = OverrunStyle.ELLIPSIS
                    }
                }
            }
        }

        right {
            hbox {
                spacing = 14.0
                alignment = Pos.CENTER_RIGHT

                hbox {
                    spacing = 7.0
                    alignment = Pos.CENTER_LEFT
                    visibleWhen(showRunningTask)
                    managedProperty().bind(visibleProperty())
                    add(ProgressBar().apply {
                        progress = ProgressBar.INDETERMINATE_PROGRESS
                        prefWidth = 64.0
                        maxWidth = 64.0
                        isFocusTraversable = false
                        addClass(Styles.SMALL)
                        visibleWhen(tasksRunning)
                        managedProperty().bind(visibleProperty())
                    })
                    add(FontIcon.of(Feather.ACTIVITY).apply {
                        styleClass.addAll("vripper-icon-statusbar", Styles.ACCENT)
                    })
                    label(runningTaskName) {
                        addClass(Styles.TEXT_SMALL, Styles.TEXT_BOLD)
                        minWidth = 0.0
                        maxWidth = 320.0
                        textOverrun = OverrunStyle.ELLIPSIS
                        tooltip = Tooltip().apply {
                            textProperty().bind(runningTaskDetail)
                        }
                    }
                }

                hbox {
                    spacing = 5.0
                    alignment = Pos.CENTER_LEFT
                    add(FontIcon.of(Feather.DOWNLOAD).apply {
                        styleClass.addAll("vripper-icon-statusbar", Styles.TEXT_MUTED)
                    })
                    label(downloadSpeed.map { "$it/s" }) {
                        addClass(Styles.TEXT_SMALL)
                    }
                }

                hbox {
                    spacing = 10.0
                    alignment = Pos.CENTER_RIGHT
                    add(statChip(Feather.ARROW_DOWN, "Downloading", running))
                    add(statChip(Feather.CLOCK, "Pending", pending))
                    add(statChip(Feather.ALERT_CIRCLE, "Error", error, alert = true))
                }
            }
        }
    }

    private fun scheduleRunningTaskHide() {
        if (showRunningTask.get()) {
            hideRunningTask.playFromStart()
        }
    }

    private fun statChip(
        icon: Feather,
        text: String,
        value: IntegerExpression,
        alert: Boolean = false,
    ): HBox {
        val iconNode = FontIcon.of(icon).apply {
            styleClass.addAll("vripper-icon-statusbar", Styles.TEXT_MUTED)
        }
        val valueLabel = label(value.asString()) {
            addClass(Styles.TEXT_SMALL, Styles.TEXT_BOLD)
            if (alert) addClass(Styles.TEXT_MUTED)
        }
        if (alert) {
            value.addListener { _, _, newValue ->
                val style = if (newValue.toInt() > 0) Styles.DANGER else Styles.TEXT_MUTED
                valueLabel.styleClass.removeAll(Styles.DANGER, Styles.TEXT_MUTED)
                valueLabel.styleClass.add(style)
                iconNode.styleClass.removeAll(Styles.DANGER, Styles.TEXT_MUTED)
                iconNode.styleClass.add(style)
            }
        }
        return hbox {
            spacing = 4.0
            alignment = Pos.CENTER_LEFT
            add(iconNode)
            label(text) {
                addClass(Styles.TEXT_SMALL, Styles.TEXT_MUTED)
            }
            add(valueLabel)
        }
    }
}
