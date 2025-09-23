package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.util.Callback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.javafx.asFlow
import me.vripper.gui.components.Shared
import me.vripper.gui.components.cells.PreviewTableCell
import me.vripper.gui.components.cells.ProgressTableCell
import me.vripper.gui.components.cells.StatusTableCell
import me.vripper.gui.components.fragments.AddLinksFragment
import me.vripper.gui.components.fragments.RenameFragment
import me.vripper.gui.controller.PostController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.Preview
import me.vripper.gui.utils.openFileDirectory
import me.vripper.gui.utils.openLink
import me.vripper.model.QueueState
import me.vripper.services.download.MovePosition
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

@OptIn(ExperimentalCoroutinesApi::class)
class PostsTableView : View() {

    override val root = vbox {}
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val postController: PostController by inject()
    private val widgetsController: WidgetsController by inject()
    private val mainView: MainView by inject()

    val tableView: TableView<PostModel>
    var items: SortedFilteredList<PostModel> = SortedFilteredList()

    private val preview: Preview = Preview(currentStage!!)

    init {
        items.filterWhen(Shared.searchInput) { query, item ->
            item.title.contains(query, ignoreCase = true)
                    || item.vgPostId.toString().contains(query)
                    || item.vgThreadId.toString().contains(query)
                    || item.hosts.contains(query, ignoreCase = true)
                    || item.status.contains(query, ignoreCase = true)
                    || item.path.contains(query, ignoreCase = true)
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

                    val bulkRenameItem = MenuItem("Auto rename").apply {
                        setOnAction {
                            bulkRenameSelected()
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

                    val moveToTop = MenuItem("Move to top").apply {
                        setOnAction {
                            coroutineScope.launch {
                                postController.moveTo(tableRow.item.id, MovePosition.TOP)
                            }
                        }
                        graphic = FontIcon.of(Feather.CHEVRONS_UP)
                    }

                    val moveUp = MenuItem("Move up").apply {
                        setOnAction {
                            coroutineScope.launch {
                                postController.moveTo(tableRow.item.id, MovePosition.UP)
                            }
                        }
                        graphic = FontIcon.of(Feather.CHEVRON_UP)
                    }

                    val moveDown = MenuItem("Move down").apply {
                        setOnAction {
                            coroutineScope.launch {
                                postController.moveTo(tableRow.item.id, MovePosition.DOWN)
                            }
                        }
                        graphic = FontIcon.of(Feather.CHEVRON_DOWN)
                    }

                    val moveToBottom = MenuItem("Move to bottom").apply {
                        setOnAction {
                            coroutineScope.launch {
                                postController.moveTo(tableRow.item.id, MovePosition.BOTTOM)
                            }
                        }
                        graphic = FontIcon.of(Feather.CHEVRONS_DOWN)
                    }

                    val contextMenu = ContextMenu()
                    contextMenu.items.addAll(
                        startItem,
                        stopItem,
                        renameItem,
                        bulkRenameItem,
                        deleteItem,
                        SeparatorMenuItem(),
                        locationItem,
                        urlItem,
                        SeparatorMenuItem(),
                        moveToTop,
                        moveUp,
                        moveDown,
                        moveToBottom
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
                        }.openModal()?.apply {
                            minWidth = 100.0
                            minHeight = 100.0
                        }
                    }
                })
                column("#", PostModel::orderProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.orderProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.orderProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.postsColumnsWidthModel.order
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.postsColumnsWidthModel.order = it as Double
                        }
                    }
                    cellFactory = Callback {
                        TextFieldTableCell<PostModel?, String?>().apply { alignment = Pos.CENTER_LEFT }
                    }
                }
                column("Preview", PostModel::previewListProperty) {
                    isVisible = widgetsController.currentSettings.postsColumnsModel.previewProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.previewProperty.set(
                            it
                        )
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
                                    cell.tableRow.item.vgThreadId,
                                    cell.tableRow.item.previewList
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
                        widgetsController.currentSettings.postsColumnsModel.titleProperty.set(
                            it
                        )
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
                        widgetsController.currentSettings.postsColumnsModel.progressProperty.set(
                            it
                        )
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
                    isVisible = widgetsController.currentSettings.postsColumnsModel.statusProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.postsColumnsModel.statusProperty.set(
                            it
                        )
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
                        widgetsController.currentSettings.postsColumnsModel.totalProperty.set(
                            it
                        )
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
                        widgetsController.currentSettings.postsColumnsModel.hostsProperty.set(
                            it
                        )
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
                        widgetsController.currentSettings.postsColumnsModel.addedOnProperty.set(
                            it
                        )
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
        tableView.prefHeightProperty().bind(root.heightProperty())
        tableView.placeholder = Label("Loading")

        coroutineScope.launch {
            launch {
                GuiEventBus.events.collect {
                    when (it) {
                        GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                            val postModelList = postController.findAllPosts().toList()
                            val queueState = postController.getQueueState()
                            runLater {
                                items.addAll(postModelList)
                                tableView.sort()
                                tableView.placeholder = Label("No content in table")
                                updateQueueState(queueState)
                            }
                        }

                        GuiEventBus.ChangingSession -> runLater {
                            tableView.placeholder = Label("Loading")
                            items.clear()
                        }

                        else -> {}
                    }
                }
            }

            launch {
                postController.updateMetadataFlow.collect { metadataEntity ->
                    runLater {
                        val postModel = items.find { it.id == metadataEntity.postIdRef } ?: return@runLater

                        postModel.altTitles = FXCollections.observableArrayList(metadataEntity.data.resolvedNames)
                        postModel.postedBy = metadataEntity.data.postedBy
                    }
                }
            }

            launch {
                postController.deletedPostsFlow.collect {
                    runLater {
                        items.items.removeIf { p -> p.id == it }
                        tableView.sort()
                    }
                }
            }

            launch {
                postController.updatePostsFlow.collect { post ->
                    runLater {
                        val postModel = items.find { it.id == post.id } ?: return@runLater

                        postModel.status = post.status.name
                        postModel.progressCount = postController.progressCount(
                            post.total, post.done, post.downloaded
                        )
                        postModel.done = post.done
                        postModel.progress = postController.progress(
                            post.total, post.done
                        )
                        postModel.path = post.getDownloadFolder()
                        postModel.folderName = post.folderName
                    }
                }
            }

            launch {
                postController.queueStateUpdate.collect { queueState ->
                    updateQueueState(queueState)
                }
            }

            launch {
                postController.newPostsFlow.collect {
                    runLater {
                        items.addAll(it)
                        tableView.sort()
                    }
                }
            }
        }
    }

    private fun updateQueueState(queueState: QueueState) {
        items.forEach { post ->
            val rank = queueState.rank.find { it.postEntityId == post.id }
            if (rank != null) {
                runLater {
                    post.order = rank.rank.toString()
                }
            } else if (post.order != "*") {
                runLater {
                    post.order = "*"
                }
            }
        }
        runLater {
            tableView.sort()
        }
    }

    private fun isCurrentTab(): Boolean = mainView.root.selectionModel.selectedItem.id == "download-tab"

    private fun rename(post: PostModel) {
        find<RenameFragment>(
            mapOf(
                RenameFragment::id to post.id,
                RenameFragment::name to post.folderName,
                RenameFragment::altTitles to post.altTitles
            )
        ).openModal()?.apply {
            minWidth = 100.0
            minHeight = 100.0
        }
    }

    fun bulkRenameSelected() {
        val selectedItems = tableView.selectionModel.selectedItems
        coroutineScope.launch {
            postController.renameToFirst(selectedItems.map { it.id })
        }
    }

    fun deleteSelected() {
        val postEntityIdList = tableView.selectionModel.selectedItems.map { it.id }
        confirm(
            "",
            "Confirm removal of ${postEntityIdList.size} post${if (postEntityIdList.size > 1) "s" else ""}?",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Remove posts"
        ) {
            coroutineScope.launch {
                postController.delete(postEntityIdList)
                runLater {
                    items.items.removeIf { postEntityIdList.contains(it.id) }
                }
            }
        }
    }

    fun stopSelected() {
        val postEntityIdList = tableView.selectionModel.selectedItems.map { it.id }
        coroutineScope.launch {
            postController.stop(postEntityIdList)
        }
    }

    fun startSelected() {
        val postEntityIdList = tableView.selectionModel.selectedItems.map { it.id }
        coroutineScope.launch {
            postController.start(postEntityIdList)
        }
    }
}