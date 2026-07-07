package me.vripper.gui.components.views

import atlantafx.base.controls.Message
import atlantafx.base.theme.Styles
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.gui.controller.TaskController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.TaskModel
import me.vripper.tasks.TaskStatus
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import tornadofx.*

class TaskTableView : View() {

    private val taskController: TaskController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val items: ObservableList<TaskModel> = FXCollections.observableArrayList()
    private val messageContainer = vbox {
        spacing = 8.0
        padding = Insets(8.0)
    }

    override val root = vbox {
        hbox {
            padding = Insets(8.0)
            spacing = 8.0
            alignment = Pos.CENTER_LEFT
            button("Clear tasks", FontIcon.of(Feather.TRASH_2)) {
                action {
                    confirm(
                        "",
                        "Are you sure you want to clear all tasks",
                        ButtonType.YES,
                        ButtonType.NO,
                        owner = primaryStage,
                        title = "Clear tasks"
                    ) {
                        coroutineScope.launch {
                            taskController.clearAll()
                        }
                    }
                }
            }
        }
        scrollpane {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            style = "-fx-background-color: transparent;"
            vgrow = Priority.ALWAYS
            content = messageContainer
        }
    }

    init {
        titleProperty.bind(items.sizeProperty.map {
            if (it.toLong() > 0) {
                "Tasks (${it.toLong()})"
            } else {
                "Tasks"
            }
        })
        refresh()

        taskController.newTask.let { flow ->
            coroutineScope.launch {
                flow.collect { task ->
                    runLater {
                        items.removeIf { it.id == task.id }
                        items.add(0, task)
                        refresh()
                    }
                }
            }
        }

        taskController.updateTask.let { flow ->
            coroutineScope.launch {
                flow.collect { task ->
                    runLater {
                        val index = items.indexOfFirst { it.id == task.id }
                        if (index >= 0) {
                            items[index] = task
                        } else {
                            items.add(0, task)
                        }
                        refresh()
                    }
                }
            }
        }

        taskController.clearTasks.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        items.clear()
                        refresh()
                    }
                }
            }
        }

        coroutineScope.launch {
            GuiEventBus.events.collect {
                when (it) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        val tasks = taskController.findAll()
                        runLater {
                            items.setAll(tasks)
                            refresh()
                        }
                    }

                    GuiEventBus.ChangingSession -> runLater {
                        items.clear()
                        refresh()
                    }

                    is GuiEventBus.RemoteError -> runLater {
                        items.clear()
                        refresh()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun refresh() {
        if (items.isEmpty()) {
            messageContainer.children.setAll(Label("No tasks"))
            return
        }
        messageContainer.children.setAll(items.map(::createMessage))
    }

    private fun createMessage(task: TaskModel): Message {
        val text = buildString {
            append(task.taskCategory)
            append(" - ")
            append(task.description)
            if (task.result.isNotBlank()) {
                append('\n')
                append(task.result)
            }
            append("\nUpdated: ")
            append(task.updatedAtFormatted())
        }
        val title = "${task.name} - ${task.status.name}"
        return when (task.status) {
            TaskStatus.WAITING, TaskStatus.RUNNING -> infoMessage(title, text)
            TaskStatus.SUCCESS -> successMessage(title, text)
            TaskStatus.WARNING -> warningMessage(title, text)
            TaskStatus.FAILURE -> dangerMessage(title, text)
        }
    }

    private fun infoMessage(title: String, text: String): Message =
        Message(title, text, FontIcon(Material2OutlinedAL.HELP_OUTLINE)).apply {
            styleClass.add(Styles.ACCENT)
            maxWidth = Double.MAX_VALUE
        }

    private fun successMessage(title: String, text: String): Message =
        Message(title, text, FontIcon(Material2OutlinedAL.CHECK_CIRCLE_OUTLINE)).apply {
            styleClass.add(Styles.SUCCESS)
            maxWidth = Double.MAX_VALUE
        }

    private fun warningMessage(title: String, text: String): Message =
        Message(title, text, FontIcon(Material2OutlinedMZ.OUTLINED_FLAG)).apply {
            styleClass.add(Styles.WARNING)
            maxWidth = Double.MAX_VALUE
        }

    private fun dangerMessage(title: String, text: String): Message =
        Message(title, text, FontIcon(Material2OutlinedAL.ERROR_OUTLINE)).apply {
            styleClass.add(Styles.DANGER)
            maxWidth = Double.MAX_VALUE
        }
}
