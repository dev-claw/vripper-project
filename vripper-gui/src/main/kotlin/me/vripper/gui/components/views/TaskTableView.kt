package me.vripper.gui.components.views

import atlantafx.base.controls.Message
import atlantafx.base.theme.Styles
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ButtonType
import javafx.scene.control.ScrollPane
import javafx.scene.layout.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.gui.components.EmptyState
import me.vripper.gui.components.Shared
import me.vripper.gui.components.TaskQuickFilter
import me.vripper.gui.components.quickFilterPills
import me.vripper.gui.controller.TaskController
import me.vripper.gui.model.TaskModel
import me.vripper.tasks.TaskStatus
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import tornadofx.*
import java.util.function.Predicate

class TaskTableView : View() {

    private val taskController: TaskController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchInput = Shared.tasksSearchInput
    private val quickFilterProperty = Shared.taskQuickFilter
    private val items: ObservableList<TaskModel> = FXCollections.observableArrayList()
    private val filteredItems = FilteredList(items)
    private val messageContainer = vbox {
        spacing = 8.0
        padding = Insets(8.0)
    }
    private val emptyMessage = SimpleStringProperty("No tasks yet")
    private val emptyState = EmptyState.create(Feather.ACTIVITY, emptyMessage).apply {
        visibleProperty().bind(Bindings.isEmpty(filteredItems))
    }

    override val root = vbox {
        hbox {
            padding = Insets(8.0)
            spacing = 8.0
            alignment = Pos.CENTER_LEFT
            button("Clear tasks", FontIcon.of(Feather.TRASH_2)) {
                action { clearTasks() }
            }
            spacer()
            add(
                quickFilterPills(
                    "Status",
                    TaskQuickFilter.entries.map { it to it.label },
                    quickFilterProperty.get(),
                ) { quickFilterProperty.set(it) }
            )
        }
        stackpane {
            vgrow = Priority.ALWAYS
            add(scrollpane {
                isFitToWidth = true
                hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
                style = "-fx-background-color: transparent;"
                content = messageContainer
            })
            add(emptyState)
        }
    }

    init {
        searchInput.addListener { _, _, _ -> applyTaskFilter() }
        quickFilterProperty.addListener { _, _, _ -> applyTaskFilter() }
        applyTaskFilter()
        titleProperty.bind(filteredItems.sizeProperty.map {
            if (it.toLong() > 0) {
                "Tasks (${it.toLong()})"
            } else {
                "Tasks"
            }
        })
        filteredItems.addListener(ListChangeListener { refresh() })
        refresh()

        coroutineScope.launch {
            taskController.tasks.collect { tasks ->
                runLater {
                    items.setAll(tasks)
                    refresh()
                }
            }
        }
    }

    private fun applyTaskFilter() {
        val query = searchInput.get().orEmpty().trim()
        val filter = quickFilterProperty.get() ?: TaskQuickFilter.ALL
        emptyMessage.set(
            if (query.isBlank() && filter == TaskQuickFilter.ALL) "No tasks yet" else "No matching tasks"
        )
        filteredItems.predicate = Predicate { task ->
            matchesQuery(task, query) && matchesQuickFilter(task, filter)
        }
    }

    private fun matchesQuery(task: TaskModel, query: String): Boolean =
        query.isBlank() ||
                task.name.contains(query, ignoreCase = true) ||
                task.description.contains(query, ignoreCase = true) ||
                task.taskCategory.name.contains(query, ignoreCase = true) ||
                task.status.name.contains(query, ignoreCase = true) ||
                task.result.contains(query, ignoreCase = true)

    private fun matchesQuickFilter(task: TaskModel, filter: TaskQuickFilter): Boolean =
        when (filter) {
            TaskQuickFilter.ALL -> true
            TaskQuickFilter.ACTIVE -> task.status == TaskStatus.WAITING || task.status == TaskStatus.RUNNING
            TaskQuickFilter.SUCCESS -> task.status == TaskStatus.SUCCESS
            TaskQuickFilter.ISSUES -> task.status == TaskStatus.WARNING || task.status == TaskStatus.FAILURE
        }

    private fun clearTasks() {
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

    private fun refresh() {
        messageContainer.children.setAll(filteredItems.map(::createMessage))
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
