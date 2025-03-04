package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.javafx.asFlow
import me.vripper.gui.components.fragments.ThreadSelectionTableFragment
import me.vripper.gui.controller.ThreadController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.utils.ActiveUICoroutines
import me.vripper.gui.utils.openLink
import me.vripper.services.IAppEndpointService
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class ThreadTableView : View() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val threadController: ThreadController by inject()
    private val widgetsController: WidgetsController by inject()
    private val mainView: MainView by inject()
    private val localAppEndpointService: IAppEndpointService by di("localAppEndpointService")
    private val remoteAppEndpointService: IAppEndpointService by di("remoteAppEndpointService")
    private val tableView: TableView<ThreadModel>
    private val items: ObservableList<ThreadModel> = FXCollections.observableArrayList()

    override val root = vbox {}

    init {
        coroutineScope.launch {
            GuiEventBus.events.collect { event ->
                when (event) {
                    is GuiEventBus.LocalSession -> {
                        threadController.appEndpointService = localAppEndpointService
                        connect()
                    }

                    is GuiEventBus.RemoteSession -> {
                        threadController.appEndpointService = remoteAppEndpointService
                        connect()
                    }

                    is GuiEventBus.ChangingSession -> {
                        ActiveUICoroutines.threads.forEach { it.cancelAndJoin() }
                        ActiveUICoroutines.threads.clear()
                        runLater {
                            tableView.placeholder = Label("Loading")
                        }
                    }
                }
            }
        }

        with(root) {
            tableView = tableview(items) {
                isTableMenuButtonVisible = true
                primaryStage.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                    if (event.code == KeyCode.DELETE) {
                        if (isCurrentTab() && selectionModel.selectedItems.isNotEmpty() && this.isFocused) {
                            deleteSelected()
                        }
                    }
                }
                addClass(Styles.DENSE, Tweaks.EDGE_TO_EDGE)
                selectionModel.selectionMode = SelectionMode.MULTIPLE
                setRowFactory {
                    val tableRow = TableRow<ThreadModel>()

                    tableRow.setOnMouseClicked {
                        if (it.clickCount == 2 && tableRow.item != null) {
                            selectPosts(tableRow.item.threadId)
                        }
                    }

                    val selectItem = MenuItem("Select posts").apply {
                        setOnAction {
                            selectPosts(tableRow.item.threadId)
                        }
                        graphic = FontIcon.of(Feather.MENU)
                    }

                    val urlItem = MenuItem("Open link").apply {
                        setOnAction {
                            openLink(tableRow.item.link)
                        }
                        graphic = FontIcon.of(Feather.LINK)
                    }

                    val deleteItem = MenuItem("Delete").apply {
                        setOnAction {
                            deleteSelected()
                        }
                        graphic = FontIcon.of(Feather.TRASH)
                    }

                    val contextMenu = ContextMenu()
                    contextMenu.items.addAll(selectItem, urlItem, SeparatorMenuItem(), deleteItem)
                    tableRow.contextMenuProperty()
                        .bind(tableRow.emptyProperty().map { empty -> if (empty) null else contextMenu })
                    tableRow
                }
                contextMenu = ContextMenu()
                contextMenu.items.add(MenuItem("Clear", FontIcon.of(Feather.TRASH_2)).apply {
                    setOnAction {
                        confirm(
                            "",
                            "Confirm removal of all threads",
                            ButtonType.YES,
                            ButtonType.NO,
                            owner = primaryStage,
                            title = "Clean threads"
                        ) {
                            coroutineScope.launch {
                                threadController.clearAll()
                            }
                        }
                    }
                })
                column("Title", ThreadModel::titleProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.titleProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.titleProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.title
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.title = it as Double
                        }
                    }
                }
                column("URL", ThreadModel::linkProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.linkProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.linkProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.link
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.link = it as Double
                        }
                    }
                }
                column("Count", ThreadModel::totalProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.countProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.countProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.count
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.count = it as Double
                        }
                    }
                }
            }
        }

        titleProperty.bind(items.sizeProperty.map {
            if (it.toLong() > 0) {
                "Threads (${it.toLong()})"
            } else {
                "Threads"
            }
        })
        tableView.prefHeightProperty().bind(root.heightProperty())
        tableView.placeholder = Label("Loading")
    }

    fun connect() {
        coroutineScope.launch {
            val list = async {
                threadController.findAll()
            }.await()
            runLater {
                items.clear()
                items.addAll(list)
                tableView.placeholder = Label("No content in table")
            }
        }
        coroutineScope.launch {
            threadController.onNewThread().collect {
                runLater {
                    items.add(it)
                }
            }
        }.also { ActiveUICoroutines.threads.add(it) }

        coroutineScope.launch {
            threadController.onUpdateThread().collect { thread ->
                runLater {
                    val threadModel = items.find { it.threadId == thread.threadId } ?: return@runLater
                    threadModel.total = thread.total
                    threadModel.title = thread.title
                }
            }
        }.also { ActiveUICoroutines.threads.add(it) }

        coroutineScope.launch {
            threadController.onDeleteThread().collect { threadId ->
                runLater {
                    tableView.items.removeIf { it.threadId == threadId }
                }
            }
        }.also { ActiveUICoroutines.threads.add(it) }

        coroutineScope.launch {
            threadController.onClearThreads().collect {
                runLater {
                    tableView.items.clear()
                }
            }
        }.also { ActiveUICoroutines.threads.add(it) }
    }

    private fun isCurrentTab(): Boolean = mainView.root.selectionModel.selectedItem.id == "thread-tab"

    private fun deleteSelected() {
        val threadIdList = tableView.selectionModel.selectedItems.map { it.threadId }
        confirm(
            "",
            "Confirm removal of ${threadIdList.size} thread${if (threadIdList.size > 1) "s" else ""}?",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Clean threads"
        ) {
            coroutineScope.launch {
                threadController.delete(threadIdList)
            }
        }
    }

    private fun selectPosts(threadId: Long) {
        find<ThreadSelectionTableFragment>(mapOf(ThreadSelectionTableFragment::threadId to threadId)).openModal()
            ?.apply {
                minWidth = 100.0
                minHeight = 100.0
            }
    }
}