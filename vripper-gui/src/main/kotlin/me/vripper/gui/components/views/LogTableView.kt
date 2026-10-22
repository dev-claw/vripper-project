package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.javafx.asFlow
import me.vripper.gui.components.EmptyState
import me.vripper.gui.components.LogQuickFilter
import me.vripper.gui.components.Shared
import me.vripper.gui.components.cells.LogLevelTableCell
import me.vripper.gui.components.fragments.LogMessageFragment
import me.vripper.gui.components.quickFilterPills
import me.vripper.gui.controller.LogController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.LogModel
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class LogTableView : View() {

    private val logController: LogController by inject()
    private val widgetsController: WidgetsController by inject()
    private val tableView: TableView<LogModel>
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchInput = Shared.logsSearchInput
    private val quickFilterProperty = Shared.logQuickFilter
    private val items: ObservableList<LogModel> = FXCollections.observableArrayList()
    private val filteredItems = SortedFilteredList(items)
    private val loadingPlaceholder = Label("Loading")
    private val connectionFailurePlaceholder = Label("Connection Failure")
    private val emptyMessage = SimpleStringProperty("No logs yet")
    private val emptyPlaceholder = EmptyState.create(Feather.TERMINAL, emptyMessage)
    private var maxLogEvent = 100

    override val root = vbox {}

    init {
        with(root) {
            hbox {
                padding = Insets(8.0)
                spacing = 8.0
                alignment = Pos.CENTER_LEFT
                button("Clear logs", FontIcon.of(Feather.TRASH_2)) {
                    action { clearLogs() }
                }
                spacer()
                add(
                    quickFilterPills(
                        "Level",
                        LogQuickFilter.entries.map { it to it.label },
                        quickFilterProperty.get(),
                    ) { quickFilterProperty.set(it) }
                )
            }
            tableView = tableview(filteredItems) {
                isTableMenuButtonVisible = true
                addClass(Styles.DENSE, Tweaks.EDGE_TO_EDGE)
                selectionModel.selectionMode = SelectionMode.SINGLE
                setRowFactory {
                    val tableRow = TableRow<LogModel>()
                    tableRow.setOnMouseClicked {
                        if (it.clickCount == 2 && tableRow.item != null) {
                            openLog(tableRow.item)
                        }
                    }
                    tableRow
                }
                column("Time", LogModel::timestampProperty) {
                    isVisible = widgetsController.currentSettings.logsColumnsModel.timeProperty.get()
                    visibleProperty().onChange { widgetsController.currentSettings.logsColumnsModel.timeProperty.set(it) }
                    id = "time"
                    prefWidth = widgetsController.currentSettings.logsColumnsWidthModel.time
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.logsColumnsWidthModel.time = it as Double
                        }
                    }
                    sortType = TableColumn.SortType.DESCENDING
                }
                column("Thread", LogModel::threadNameProperty) {
                    isVisible = widgetsController.currentSettings.logsColumnsModel.threadNameProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.logsColumnsModel.threadNameProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.logsColumnsWidthModel.threadName
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.logsColumnsWidthModel.threadName = it as Double
                        }
                    }
                    id = "type"
                    isSortable = false
                }
                column("Logger", LogModel::loggerNameProperty) {
                    isVisible = widgetsController.currentSettings.logsColumnsModel.loggerNameProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.logsColumnsModel.loggerNameProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.logsColumnsWidthModel.loggerName
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.logsColumnsWidthModel.loggerName = it as Double
                        }
                    }
                    id = "status"
                    isSortable = false
                }
                column("Level", LogModel::levelStringProperty) {
                    isVisible = widgetsController.currentSettings.logsColumnsModel.levelStringProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.logsColumnsModel.levelStringProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.logsColumnsWidthModel.levelString
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.logsColumnsWidthModel.levelString = it as Double
                        }
                    }
                    id = "status"
                    isSortable = false
                    setCellFactory {
                        LogLevelTableCell()
                    }
                }
                column("Message", LogModel::formattedMessageProperty) {
                    isVisible = widgetsController.currentSettings.logsColumnsModel.messageProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.logsColumnsModel.messageProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.logsColumnsWidthModel.message
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.logsColumnsWidthModel.message = it as Double
                        }
                    }
                    id = "status"
                    isSortable = false
                    setCellFactory {
                        object : TableCell<LogModel, String>() {
                            private val levelStyles = listOf(
                                Styles.WARNING,
                                Styles.DANGER,
                                Styles.TEXT_MUTED,
                            )

                            override fun updateItem(item: String?, empty: Boolean) {
                                super.updateItem(item, empty)
                                styleClass.removeAll(levelStyles)
                                text = if (empty) null else item
                                if (empty) return
                                when (tableRow?.item?.levelString?.uppercase()) {
                                    "WARN", "WARNING" -> styleClass.add(Styles.WARNING)
                                    "ERROR", "FATAL" -> styleClass.add(Styles.DANGER)
                                    "DEBUG", "TRACE" -> styleClass.add(Styles.TEXT_MUTED)
                                }
                            }
                        }
                    }
                }
            }
            tableView.maxHeight = Double.MAX_VALUE
            VBox.setVgrow(tableView, Priority.ALWAYS)
        }
        searchInput.addListener { _, _, _ -> applyLogFilter() }
        quickFilterProperty.addListener { _, _, _ -> applyLogFilter() }
        applyLogFilter()
        titleProperty.bind(filteredItems.sizeProperty.map {
            if (it.toLong() > 0) {
                "Log (${it.toLong()})"
            } else {
                "Log"
            }
        })
        tableView.placeholder = loadingPlaceholder
        tableView.sortOrder.add(tableView.columns.first { it.id == "time" })

        logController.newLogs.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        items.sortWith(Comparator.comparing { it.sequence })
                        while (items.isNotEmpty() && (items.size >= maxLogEvent)) {
                            items.removeFirst()
                        }
                        items.add(it)
                    }
                }
            }
        }

        logController.updateSettings.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    maxLogEvent = it.systemSettings.maxEventLog
                }
            }
        }

        coroutineScope.launch {
            GuiEventBus.events.collect {
                when (it) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        while (isActive) {
                            val result = runCatching { logController.getMaxEventLog() }
                            if (result.isSuccess) {
                                maxLogEvent = result.getOrNull()!!
                                break
                            }
                        }
                        while (isActive) {
                            val result = runCatching { logController.initLogger() }
                            if (result.isSuccess) {
                                break
                            }
                        }
                        runLater {
                            tableView.placeholder = emptyPlaceholder
                        }
                    }

                    GuiEventBus.ChangingSession -> runLater {
                        items.clear()
                        tableView.placeholder = loadingPlaceholder
                    }

                    is GuiEventBus.RemoteError -> runLater {
                        items.clear()
                        tableView.placeholder = connectionFailurePlaceholder
                    }

                    else -> {}
                }
            }
        }
    }

    private fun applyLogFilter() {
        val query = searchInput.get().orEmpty().trim()
        val filter = quickFilterProperty.get() ?: LogQuickFilter.ALL
        emptyMessage.set(
            if (query.isBlank() && filter == LogQuickFilter.ALL) "No logs yet" else "No matching logs"
        )
        filteredItems.predicate = { log ->
            matchesQuery(log, query) && matchesQuickFilter(log, filter)
        }
    }

    private fun matchesQuery(log: LogModel, query: String): Boolean =
        query.isBlank() ||
                log.formattedMessage.contains(query, ignoreCase = true) ||
                log.loggerName.contains(query, ignoreCase = true) ||
                log.threadName.contains(query, ignoreCase = true) ||
                log.levelString.contains(query, ignoreCase = true) ||
                log.throwable.contains(query, ignoreCase = true)

    private fun matchesQuickFilter(log: LogModel, filter: LogQuickFilter): Boolean {
        val level = log.levelString.uppercase()
        return when (filter) {
            LogQuickFilter.ALL -> true
            LogQuickFilter.ERRORS -> level == "ERROR" || level == "FATAL"
            LogQuickFilter.WARNINGS -> level == "WARN" || level == "WARNING"
            LogQuickFilter.INFO -> level == "INFO"
            LogQuickFilter.DEBUG -> level == "DEBUG" || level == "TRACE"
        }
    }

    private fun clearLogs() {
        confirm(
            "",
            "Are you sure you want to clear all logs",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Clear logs"
        ) {
            items.clear()
            tableView.placeholder = emptyPlaceholder
        }
    }

    private fun openLog(item: LogModel) {
        find<LogMessageFragment>(mapOf(LogMessageFragment::logModel to item)).openModal()?.apply {
            minWidth = 100.0
            minHeight = 100.0
        }
    }
}
