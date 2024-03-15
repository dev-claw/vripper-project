package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.input.MouseButton
import javafx.util.Callback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.javafx.asFlow
import me.vripper.gui.components.cells.PreviewTableCell
import me.vripper.gui.components.cells.ProgressTableCell
import me.vripper.gui.components.cells.StatusTableCell
import me.vripper.gui.components.fragments.AddLinksFragment
import me.vripper.gui.components.fragments.ColumnSelectionFragment
import me.vripper.gui.components.fragments.RenameFragment
import me.vripper.gui.controller.PostController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.PostModel
import me.vripper.gui.services.ClipboardService
import me.vripper.gui.utils.Preview
import me.vripper.gui.utils.openFileDirectory
import me.vripper.gui.utils.openLink
import me.vripper.services.IAppEndpointService
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*
import kotlin.io.path.Path

class PostsTableView : View() {

    private val coroutineScope = CoroutineScope(SupervisorJob())
    private val postController: PostController by inject()
    private val widgetsController: WidgetsController by inject()
    private val clipboardService: ClipboardService by inject()
    private val localAppEndpointService: IAppEndpointService by di("localAppEndpointService")
    private val remoteAppEndpointService: IAppEndpointService by di("remoteAppEndpointService")
    private val jobs = mutableListOf<Job>()

    val tableView: TableView<PostModel>
    var items: ObservableList<PostModel> = FXCollections.observableArrayList()
    private var preview: Preview? = null

    override val root = vbox {}

    init {
        coroutineScope.launch {
            GuiEventBus.events.collect { event ->
                when (event) {
                    is GuiEventBus.LocalSession -> {
                        postController.appEndpointService = localAppEndpointService
                        connect()
                    }

                    is GuiEventBus.RemoteSession -> {
                        postController.appEndpointService = remoteAppEndpointService
                        connect()
                    }

                    is GuiEventBus.ChangingSession -> {
                        jobs.forEach { it.cancel() }
                        jobs.clear()
                    }
                }
            }
        }

        with(root) {
            tableView = tableview(items) {
                addClass(Styles.DENSE, Styles.STRIPED)
                selectionModel.selectionMode = SelectionMode.MULTIPLE
                setRowFactory {
                    val tableRow = TableRow<PostModel>()

                    val startItem = MenuItem("Start").apply {
                        setOnAction {
                            startSelected()
                        }
                        graphic = FontIcon.of(Feather.PLAY)
                    }

                    val stopItem = MenuItem("Stop").apply {
                        setOnAction {
                            stopSelected()
                        }
                        graphic = FontIcon.of(Feather.SQUARE)
                    }

                    val renameItem = MenuItem("Rename").apply {
                        setOnAction {
                            rename(tableRow.item)
                        }
                        graphic = FontIcon.of(Feather.EDIT)
                    }

                    val deleteItem = MenuItem("Delete").apply {
                        setOnAction {
                            deleteSelected()
                        }
                        graphic = FontIcon.of(Feather.TRASH)
                    }

                    val locationItem = MenuItem("Open containing folder").apply {
                        visibleWhen(widgetsController.currentSettings.localSessionProperty)
                        setOnAction {
                            openFileDirectory(tableRow.item.path)
                        }
                        graphic = FontIcon.of(Feather.FOLDER)
                    }

                    val urlItem = MenuItem("Open link").apply {
                        setOnAction {
                            openLink(tableRow.item.url)
                        }
                        graphic = FontIcon.of(Feather.LINK)
                    }

                    val contextMenu = ContextMenu()
                    contextMenu.items.addAll(
                        startItem, stopItem, renameItem, deleteItem, SeparatorMenuItem(), locationItem, urlItem
                    )
                    tableRow.contextMenuProperty()
                        .bind(tableRow.emptyProperty().map { empty -> if (empty) null else contextMenu })
                    tableRow
                }
                contextMenu = ContextMenu()
                contextMenu.items.addAll(MenuItem("Add links").apply {
                    graphic = FontIcon.of(Feather.PLUS)
                    action {
                        find<AddLinksFragment>().apply {
                            input.clear()
                        }.openModal()
                    }
                }, SeparatorMenuItem(), MenuItem("Setup columns").apply {
                    graphic = FontIcon.of(Feather.COLUMNS)
                    setOnAction {
                        find<ColumnSelectionFragment>(
                            mapOf(
                                ColumnSelectionFragment::map to mapOf(
                                    Pair(
                                        "Preview", widgetsController.currentSettings.postsColumnsModel.previewProperty
                                    ),
                                    Pair(
                                        "Title", widgetsController.currentSettings.postsColumnsModel.titleProperty
                                    ),
                                    Pair(
                                        "Progress", widgetsController.currentSettings.postsColumnsModel.progressProperty
                                    ),
                                    Pair(
                                        "Status", widgetsController.currentSettings.postsColumnsModel.statusProperty
                                    ),
                                    Pair(
                                        "Path", widgetsController.currentSettings.postsColumnsModel.pathProperty
                                    ),
                                    Pair(
                                        "Total", widgetsController.currentSettings.postsColumnsModel.totalProperty
                                    ),
                                    Pair(
                                        "Hosts", widgetsController.currentSettings.postsColumnsModel.hostsProperty
                                    ),
                                    Pair(
                                        "Added On", widgetsController.currentSettings.postsColumnsModel.addedOnProperty
                                    ),
                                    Pair(
                                        "Order", widgetsController.currentSettings.postsColumnsModel.orderProperty
                                    ),
                                )
                            )
                        ).openModal()
                    }
                })
                column("Preview", PostModel::previewListProperty) {
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.previewProperty)
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.preview
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.preview = it as Double
                        }
                    }
                    cellFactory = Callback {
                        val cell = PreviewTableCell<PostModel>()
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
                column("Title", PostModel::titleProperty) {
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.titleProperty)
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
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.progressProperty)
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
                                    when (it.clickCount) {
                                        1 -> {
                                            this@tableview.requestFocus()
                                            this@tableview.focusModel.focus(cell.tableRow.index)
                                            if (it.isControlDown && it.button.equals(MouseButton.PRIMARY)) {
                                                if (this@tableview.selectionModel.isSelected(cell.tableRow.index)) {
                                                    this@tableview.selectionModel.clearSelection(cell.tableRow.index)
                                                } else {
                                                    this@tableview.selectionModel.select(cell.tableRow.index)
                                                }
                                            } else if (it.button.equals(MouseButton.PRIMARY)) {
                                                this@tableview.selectionModel.clearSelection()
                                                this@tableview.selectionModel.select(cell.tableRow.index)
                                            }
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
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.statusProperty)
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
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.pathProperty)
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
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.totalProperty)
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
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.hostsProperty)
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
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.addedOnProperty)
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.addedOn
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.addedOn = it as Double
                        }
                    }
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
                column("Order", PostModel::orderProperty) {
                    visibleProperty().bind(widgetsController.currentSettings.postsColumnsModel.orderProperty)
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.order
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.order = it as Double
                        }
                    }
                    sortType = TableColumn.SortType.DESCENDING
                    sortOrder.add(this)
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, Number?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
            }
        }
        tableView.prefHeightProperty().bind(root.heightProperty())
        tableView.placeholder = Label("Loading")
    }

    private fun connect() {
        coroutineScope.launch {
            val postModelList = async { postController.findAllPosts() }.await()
            runLater {
                items.clear()
                items.addAll(postModelList)
                tableView.sort()
                tableView.placeholder = Label("No content in table")
                clipboardService.init(postController.appEndpointService)
            }
        }

        coroutineScope.launch {
            postController.onNewPosts().collect {
                runLater {
                    tableView.items.addAll(it)
                    tableView.sort()
                }
            }
        }.also { jobs.add(it) }

        coroutineScope.launch {
            postController.onUpdatePosts().collect { post ->
                runLater {
                    val postModel = items.find { it.postId == post.postId } ?: return@runLater

                    postModel.status = post.status.name
                    postModel.progressCount = postController.progressCount(
                        post.total, post.done, post.downloaded
                    )
                    postModel.order = post.rank + 1
                    postModel.done = post.done
                    postModel.progress = postController.progress(
                        post.total, post.done
                    )
                    postModel.path = post.getDownloadFolder()
                    postModel.folderName = post.folderName
                }
            }
        }.also { jobs.add(it) }

        coroutineScope.launch {
            postController.onDeletePosts().collect {
                runLater {
                    items.removeIf { p -> p.postId == it }
                    tableView.sort()
                }
            }
        }.also { jobs.add(it) }

        coroutineScope.launch {
            postController.onUpdateMetadata().collect {
                runLater {
                    val postModel = items.find { it.postId == it.postId } ?: return@runLater

                    postModel.altTitles = FXCollections.observableArrayList(it.data.resolvedNames)
                    postModel.postedBy = it.data.postedBy
                }
            }
        }.also { jobs.add(it) }
    }

    private fun rename(post: PostModel) {
        find<RenameFragment>(
            mapOf(
                RenameFragment::postId to post.postId,
                RenameFragment::name to post.folderName,
                RenameFragment::altTitles to post.altTitles
            )
        ).openModal()?.apply {
            minWidth = 450.0
        }
    }

    fun renameSelected() {
        val selectedItem = tableView.selectionModel.selectedItem
        if (selectedItem != null) {
            rename(selectedItem)
        }
    }

    fun deleteSelected() {
        val postIdList = tableView.selectionModel.selectedItems.map { it.postId }
        confirm(
            "",
            "Confirm removal of ${postIdList.size} post${if (postIdList.size > 1) "s" else ""}?",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Remove posts"
        ) {
            coroutineScope.launch {
                postController.delete(postIdList)
                runLater {
                    tableView.items.removeIf { postIdList.contains(it.postId) }
                }
            }
        }
    }

    fun stopSelected() {
        val postIdList = tableView.selectionModel.selectedItems.map { it.postId }
        coroutineScope.launch {
            postController.stop(postIdList)
        }
    }

    fun startSelected() {
        val postIdList = tableView.selectionModel.selectedItems.map { it.postId }
        coroutineScope.launch {
            postController.start(postIdList)
        }
    }
}