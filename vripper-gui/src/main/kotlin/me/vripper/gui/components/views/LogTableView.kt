package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import io.grpc.StatusException
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.javafx.asFlow
import me.vripper.gui.components.fragments.LogMessageFragment
import me.vripper.gui.controller.LogController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.LogModel
import me.vripper.gui.utils.ActiveUICoroutines
import me.vripper.services.IAppEndpointService
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class LogTableView : View() {

    private val logController: LogController by inject()
    private val widgetsController: WidgetsController by inject()
    private val localAppEndpointService: IAppEndpointService by di("localAppEndpointService")
    private val remoteAppEndpointService: IAppEndpointService by di("remoteAppEndpointService")
    private val tableView: TableView<LogModel>
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val items: ObservableList<LogModel> = FXCollections.observableArrayList()
    private var maxLogEvent = 0

    override val root = vbox {}

    init {
        with(root) {
            tableView = tableview(items) {
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
                contextMenu = ContextMenu()
                contextMenu.items.add(MenuItem("Clear", FontIcon.of(Feather.TRASH_2)).apply {
                    setOnAction {
                        confirm(
                            "",
                            "Are you sure you want to clear the logs",
                            ButtonType.YES,
                            ButtonType.NO,
                            owner = primaryStage,
                            title = "Clear logs"
                        ) {
                            coroutineScope.launch {
                                coroutineScope {
                                    runLater {
                                        items.clear()
                                    }
                                }
                            }
                        }
                    }
                })
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
                }
            }
        }
        titleProperty.bind(items.sizeProperty.map {
            if (it.toLong() > 0) {
                "Log (${it.toLong()})"
            } else {
                "Log"
            }
        })
        tableView.prefHeightProperty().bind(root.heightProperty())
        tableView.placeholder = Label("Loading")
        tableView.sortOrder.add(tableView.columns.first { it.id == "time" })
    }

    override fun onDock() {
        coroutineScope.launch {
            GuiEventBus.events.collect { event ->
                when (event) {
                    is GuiEventBus.LocalSession -> {
                        runLater {
                            tableView.placeholder = Label("No Content in table")
                        }
                        logController.appEndpointService = localAppEndpointService
                        connect()
                    }

                    is GuiEventBus.RemoteSession -> {
                        runLater {
                            tableView.placeholder = Label("No Content in table")
                        }
                        logController.appEndpointService = remoteAppEndpointService
                        connect()
                    }

                    is GuiEventBus.ChangingSession -> {
                        ActiveUICoroutines.cancelLog()
                        runLater {
                            tableView.placeholder = Label("Loading")
                            items.clear()
                        }
                    }
                }
            }
        }
    }

    private fun connect() {
        runBlocking {
            maxLogEvent = logController.appEndpointService.getSettings().systemSettings.maxEventLog
        }

        connectToNewLogs()
        connectToSettingsUpdate()

        runLater {
            runBlocking {
                logController.initLogger()
            }
        }
    }

    private fun connectToSettingsUpdate() {
        coroutineScope.launch {
            logController.onUpdateSettings().catch {
                ActiveUICoroutines.removeFromLog(currentCoroutineContext().job)

                if (it is StatusException) {
                    //reconnect
                    coroutineScope.launch {
                        delay(1000)
                        connectToSettingsUpdate()
                    }
                }
            }.collect {
                maxLogEvent = it.systemSettings.maxEventLog
            }
        }.also { runBlocking { ActiveUICoroutines.addToLog(it) } }
    }

    private fun connectToNewLogs() {
        coroutineScope.launch {
            logController.onNewLog().catch {
                ActiveUICoroutines.removeFromLog(currentCoroutineContext().job)

                if (it is StatusException) {
                    //reconnect
                    coroutineScope.launch {
                        delay(1000)
                        connectToNewLogs()
                    }
                }
            }.collect {
                runLater {
                    items.sortWith(Comparator.comparing { it.sequence })
                    while (items.isNotEmpty() && (items.size >= maxLogEvent)) {
                        items.removeFirst()
                    }
                    items.add(it)
                    tableView.sort()
                }
            }
        }.also { runBlocking { ActiveUICoroutines.addToLog(it) } }
    }

    private fun openLog(item: LogModel) {
        find<LogMessageFragment>(mapOf(LogMessageFragment::logModel to item)).openModal()?.apply {
            minWidth = 100.0
            minHeight = 100.0
        }
    }
}
