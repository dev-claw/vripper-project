package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.input.MouseButton
import javafx.scene.layout.Priority
import javafx.util.Callback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.javafx.asFlow
import me.vripper.gui.components.cells.PreviewTableCell
import me.vripper.gui.controller.ThreadController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.ThreadSelectionModel
import me.vripper.gui.utils.Preview
import me.vripper.gui.utils.openLink
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*
import kotlin.io.path.Path

class ThreadSelectionTableFragment : Fragment("Thread"), KoinComponent {

    private lateinit var tableView: TableView<ThreadSelectionModel>
    private val threadController: ThreadController by inject()
    private val widgetsController: WidgetsController by inject<WidgetsController>()
    private var items = SortedFilteredList<ThreadSelectionModel>()
    private var preview: Preview? = null
    private val searchInput = SimpleStringProperty()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val threadId: Long by param()

    override fun onDock() {
        tableView.prefWidthProperty().bind(root.widthProperty())
        tableView.prefHeightProperty().bind(root.heightProperty())
        tableView.placeholder = Label("Loading")
        coroutineScope.launch {
            val list = async { threadController.grab(threadId) }.await()
            runLater {
                items.addAll(list)
            }
        }
    }

    override fun onUndock() {
        coroutineScope.cancel()
    }

    override val root = vbox(alignment = Pos.CENTER_RIGHT, spacing = 15.0) {
        hbox(spacing = 5, alignment = Pos.BASELINE_LEFT) {
            padding = Insets(10.0, 5.0, 0.0, 5.0)
            textfield(searchInput) {
                promptText = "Search"
                hgrow = Priority.ALWAYS
            }
        }

        tableView = tableview(items) {
            isTableMenuButtonVisible = true
            addClass(Styles.DENSE)
            selectionModel.selectionMode = SelectionMode.MULTIPLE
            setRowFactory {
                val tableRow = TableRow<ThreadSelectionModel>()

                tableRow.setOnMouseClicked {
                    if (it.button.equals(MouseButton.PRIMARY) && it.clickCount == 2 && tableRow.item != null) {
                        coroutineScope.launch {
                            async { threadController.download(listOf(tableRow.item)) }.await()
                            runLater {
                                close()
                            }
                        }
                    }
                }

                val urlItem = MenuItem("Open link").apply {
                    setOnAction {
                        openLink(tableRow.item.url)
                    }
                    graphic = FontIcon.of(Feather.LINK)
                }
                val contextMenu = ContextMenu()
                contextMenu.items.addAll(urlItem)
                tableRow.contextMenuProperty().bind(
                    tableRow.emptyProperty()
                        .map { empty -> if (empty) null else contextMenu })

                tableRow
            }
            column("Preview", ThreadSelectionModel::previewListProperty) {
                isVisible = widgetsController.currentSettings.threadSelectionColumnsModel.previewProperty.get()
                visibleProperty().onChange {
                    widgetsController.currentSettings.threadSelectionColumnsModel.previewProperty.set(
                        it
                    )
                }
                prefWidth = 100.0
                cellFactory = Callback {
                    val cell = PreviewTableCell<ThreadSelectionModel>()
                    cell.onMouseExited = EventHandler {
                        preview?.hide()
                    }
                    cell.onMouseMoved = EventHandler {
                        preview?.previewPopup?.apply {
                            x = it.screenX + 20
                            y = it.screenY + 10
                        }
                    }
                    cell.onMouseEntered = EventHandler { mouseEvent ->
                        preview?.hide()
                        if (cell.tableRow.item != null && cell.tableRow.item.previewList.isNotEmpty()) {
                            preview = Preview(
                                currentStage!!,
                                cell.tableRow.item.previewList,
                                Path(widgetsController.currentSettings.cachePath)
                            )
                            preview?.previewPopup?.apply {
                                x = mouseEvent.screenX + 20
                                y = mouseEvent.screenY + 10
                            }
                        }
                    }
                    cell
                }
            }
            column("Post Index", ThreadSelectionModel::indexProperty) {
                isVisible = widgetsController.currentSettings.threadSelectionColumnsModel.indexProperty.get()
                visibleProperty().onChange {
                    widgetsController.currentSettings.threadSelectionColumnsModel.indexProperty.set(
                        it
                    )
                }
                prefWidth = widgetsController.currentSettings.threadSelectionColumnsWidthModel.index
                coroutineScope.launch {
                    widthProperty().asFlow().debounce(200).collect {
                        widgetsController.currentSettings.threadSelectionColumnsWidthModel.index = it as Double
                    }
                }
                sortOrder.add(this)
                cellFactory = Callback {
                    TextFieldTableCell<ThreadSelectionModel?, Number?>().apply { alignment = Pos.CENTER_LEFT }
                }
            }
            column("Title", ThreadSelectionModel::titleProperty) {
                isVisible = widgetsController.currentSettings.threadSelectionColumnsModel.titleProperty.get()
                visibleProperty().onChange {
                    widgetsController.currentSettings.threadSelectionColumnsModel.titleProperty.set(
                        it
                    )
                }
                prefWidth = widgetsController.currentSettings.threadSelectionColumnsWidthModel.title
                coroutineScope.launch {
                    widthProperty().asFlow().debounce(200).collect {
                        widgetsController.currentSettings.threadSelectionColumnsWidthModel.title = it as Double
                    }
                }
                cellFactory = Callback {
                    TextFieldTableCell<ThreadSelectionModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                }
            }
            column("URL", ThreadSelectionModel::urlProperty) {
                isVisible = widgetsController.currentSettings.threadSelectionColumnsModel.linkProperty.get()
                visibleProperty().onChange {
                    widgetsController.currentSettings.threadSelectionColumnsModel.linkProperty.set(
                        it
                    )
                }
                prefWidth = widgetsController.currentSettings.threadSelectionColumnsWidthModel.link
                coroutineScope.launch {
                    widthProperty().asFlow().debounce(200).collect {
                        widgetsController.currentSettings.threadSelectionColumnsWidthModel.link = it as Double
                    }
                }
                cellFactory = Callback {
                    TextFieldTableCell<ThreadSelectionModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                }
            }
            column("Hosts", ThreadSelectionModel::hostsProperty) {
                isVisible = widgetsController.currentSettings.threadSelectionColumnsModel.hostsProperty.get()
                visibleProperty().onChange {
                    widgetsController.currentSettings.threadSelectionColumnsModel.hostsProperty.set(
                        it
                    )
                }
                prefWidth = widgetsController.currentSettings.threadSelectionColumnsWidthModel.hosts
                coroutineScope.launch {
                    widthProperty().asFlow().debounce(200).collect {
                        widgetsController.currentSettings.threadSelectionColumnsWidthModel.hosts = it as Double
                    }
                }
                cellFactory = Callback {
                    TextFieldTableCell<ThreadSelectionModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                }
            }
        }
        borderpane {
            right {
                padding = insets(top = 0, right = 5, bottom = 5, left = 5)
                button("Download") {
                    graphic = FontIcon.of(Feather.DOWNLOAD)
                    addClass(Styles.ACCENT)
                    isDefaultButton = true
                    tooltip("Download selected posts")
                    enableWhen { tableView.selectionModel.selectedItems.sizeProperty.greaterThan(0) }
                    action {
                        coroutineScope.launch {
                            async { threadController.download(tableView.selectionModel.selectedItems) }.await()
                            runLater {
                                close()
                            }
                        }
                    }
                }
            }
        }

        searchInput.onChange { search ->
            if (search != null) {
                items.predicate = { it.title.contains(search, true) }
            }
        }
        items.bindTo(tableView)
    }
}