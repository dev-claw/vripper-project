package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.util.Callback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.javafx.asFlow
import me.vripper.gui.components.PostsPlaceholder
import me.vripper.gui.components.cells.PreviewTableCell
import me.vripper.gui.components.cells.ProgressTableCell
import me.vripper.gui.components.cells.StatusTableCell
import me.vripper.gui.components.fragments.AddLinksFragment
import me.vripper.gui.controller.PostsTabController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.Preview
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PostsTableView : View("Posts") {

    override val root = vbox {}

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val postsController: PostsTabController by inject()
    private val widgetsController: WidgetsController by inject()
    private val mainView: MainView by inject()

    val tableView: TableView<PostModel>
    private val preview: Preview = Preview(currentStage!!)

    private var syncingSelection = false

    init {
        with(root) {
            tableView = tableview(postsController.items) {
                isTableMenuButtonVisible = true
                primaryStage.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                    if (event.code == KeyCode.DELETE) {
                        if (isCurrentTab() && selectionModel.selectedItems.isNotEmpty() && this.isFocused) {
                            postsController.deleteSelected()
                        }
                    }
                }
                addClass(Styles.DENSE, Tweaks.EDGE_TO_EDGE)
                selectionModel.selectionMode = SelectionMode.MULTIPLE
                setRowFactory {
                    val tableRow = TableRow<PostModel>()
                    tableRow.onDoubleClick {
                        tableRow.item?.let { postsController.openOverview(it) }
                    }
                    tableRow.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
                        if (event.button == MouseButton.SECONDARY &&
                            !tableRow.isEmpty &&
                            !selectionModel.isSelected(tableRow.index)
                        ) {
                            selectionModel.clearSelection()
                            selectionModel.select(tableRow.index)
                            selectionModel.focus(tableRow.index)
                        }
                    }
                    tableRow.contextMenu = postsController.buildContextMenu { tableRow.item }
                    tableRow
                }
                contextMenu = ContextMenu()
                contextMenu.items.addAll(MenuItem("Add links").apply {
                    graphic = FontIcon.of(Feather.PLUS)
                    action {
                        find<AddLinksFragment>().apply {
                            input.clear()
                        }.openModal()?.apply {
                            minWidth = 100.0
                            minHeight = 100.0
                        }
                    }
                })
                column("#", PostModel::orderProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.orderProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.orderProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.order
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.order = it as Double
                        }
                    }
                    cellFactory = Callback {
                        object : TableCell<PostModel, Number>() {
                            override fun updateItem(item: Number?, empty: Boolean) {
                                super.updateItem(item, empty)
                                text = when {
                                    empty -> null
                                    item == null -> null
                                    item.toLong() == -1L -> "*"
                                    else -> item.toString()
                                }
                                alignment = Pos.CENTER_LEFT
                            }
                        }
                    }
                    comparator = Comparator { a, b ->
                        when {
                            a == null && b == null -> 0
                            a == null -> 1
                            b == null -> -1
                            a.toLong() == -1L && b.toLong() == -1L -> 0
                            a.toLong() == -1L -> if (sortType == TableColumn.SortType.ASCENDING) 1 else -1
                            b.toLong() == -1L -> if (sortType == TableColumn.SortType.ASCENDING) -1 else 1
                            else -> a.toLong().compareTo(b.toLong())
                        }
                    }
                }
                column("Preview", PostModel::previewListProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.previewProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.previewProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.preview
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.preview = it as Double
                        }
                    }
                    cellFactory = Callback {
                        val cell = PreviewTableCell<PostModel, ObservableList<String>>()
                        cell.onMouseExited = EventHandler {
                            preview.cleanup()
                        }
                        cell.onMouseMoved = EventHandler {
                            preview.previewPopup.apply {
                                x = it.screenX + 20
                                y = it.screenY + 10
                            }
                        }
                        cell.onMouseEntered = EventHandler { mouseEvent ->
                            preview.cleanup()
                            if (cell.tableRow.item != null && cell.tableRow.item.previewList.isNotEmpty()) {
                                preview.display(
                                    cell.tableRow.item.vgThreadId, cell.tableRow.item.previewList.take(4)
                                )
                                preview.previewPopup.apply {
                                    x = mouseEvent.screenX + 20
                                    y = mouseEvent.screenY + 10
                                }
                            }
                        }
                        cell
                    }
                }
                column("Title", PostModel::titleProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.titleProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.titleProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.title
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.title = it as Double
                        }
                    }
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
                column("Progress", PostModel::progressProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.progressProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.progressProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.progress
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.progress = it as Double
                        }
                    }
                    cellFactory = Callback {
                        val cell = ProgressTableCell<PostModel>()
                        cell.alignment = Pos.CENTER
                        cell.setOnMouseClick {
                            when (it.button) {
                                MouseButton.SECONDARY -> {
                                    this@tableview.requestFocus()
                                    this@tableview.focusModel.focus(cell.tableRow.index)
                                    if (!this@tableview.selectionModel.isSelected(cell.tableRow.index)) {
                                        this@tableview.selectionModel.clearSelection()
                                        this@tableview.selectionModel.select(cell.tableRow.index)
                                    }
                                }

                                MouseButton.PRIMARY -> {
                                    if (it.clickCount == 1) {
                                        this@tableview.requestFocus()
                                        this@tableview.focusModel.focus(cell.tableRow.index)
                                        if (it.isControlDown) {
                                            if (this@tableview.selectionModel.isSelected(cell.tableRow.index)) {
                                                this@tableview.selectionModel.clearSelection(cell.tableRow.index)
                                            } else {
                                                this@tableview.selectionModel.select(cell.tableRow.index)
                                            }
                                        } else {
                                            this@tableview.selectionModel.clearSelection()
                                            this@tableview.selectionModel.select(cell.tableRow.index)
                                        }
                                    }
                                }

                                else -> {}
                            }
                        }
                        cell as TableCell<PostModel, Number>
                    }
                }
                column("Status", PostModel::statusProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.statusProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.statusProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.status
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.status = it as Double
                        }
                    }
                    cellFactory = Callback {
                        StatusTableCell<PostModel>()
                    }
                }
                column("Path", PostModel::pathProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.pathProperty.get()
                    visibleProperty().onChange { widgetsController.currentSettings.postsColumnsModel.pathProperty.set(it) }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.path
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.path = it as Double
                        }
                    }
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
                column("Total", PostModel::progressCountProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.totalProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.totalProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.total
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.total = it as Double
                        }
                    }
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
                column("Hosts", PostModel::hostsProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.hostsProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.hostsProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.hosts
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.hosts = it as Double
                        }
                    }
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
                column("Added On", PostModel::addedOnProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.addedOnProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.addedOnProperty.set(it)
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.addedOn
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.addedOn = it as Double
                        }
                    }
                    sortType = TableColumn.SortType.DESCENDING
                    sortOrder.add(this)
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
            }
        }
        postsController.bindSortRequest { tableView.sort() }
        postsController.requestSort()
        tableView.prefHeightProperty().bind(root.heightProperty())
        tableView.placeholder = PostsPlaceholder(
            postsController.placeholderStateProperty,
            postsController.emptyMessage,
        )
        tableView.selectionModel.selectedItems.addListener(ListChangeListener { publishSelection() })
        tableView.selectionModel.selectedItemProperty().onChange { publishSelection() }
        publishSelection()
    }

    fun syncSelectionFromController(requestFocus: Boolean = true) {
        syncingSelection = true
        try {
            val ids = postsController.selectedPostIds()
            val model = postsController.items
            tableView.selectionModel.clearSelection()
            var activeIndex = -1
            val visibleSelection = mutableListOf<PostModel>()
            model.forEachIndexed { index, post ->
                if (post.id in ids) {
                    tableView.selectionModel.select(index)
                    visibleSelection.add(post)
                }
                if (post.id == postsController.activePost?.id) {
                    activeIndex = index
                }
            }
            if (activeIndex < 0 && ids.isNotEmpty()) {
                activeIndex = model.indexOfFirst { it.id in ids }
            }
            if (activeIndex >= 0) {
                tableView.selectionModel.focus(activeIndex)
            }
            if (visibleSelection.size != ids.size) {
                postsController.setSelection(
                    visibleSelection,
                    postsController.activePost?.takeIf { it.id in ids } ?: visibleSelection.lastOrNull()
                )
            }
            if (requestFocus) tableView.requestFocus()
        } finally {
            syncingSelection = false
        }
    }

    private fun publishSelection() {
        if (syncingSelection || !root.isVisible) return
        val selected = tableView.selectionModel.selectedItems.toList()
        val active = tableView.selectionModel.selectedItem ?: selected.lastOrNull()
        postsController.setSelection(selected, active)
    }

    private fun isCurrentTab(): Boolean = mainView.root.selectionModel.selectedItem.id == "download-tab"
}
