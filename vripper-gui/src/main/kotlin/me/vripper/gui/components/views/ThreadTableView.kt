package me.vripper.gui.components.views

import javafx.beans.binding.Bindings

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
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.javafx.asFlow
import kotlinx.coroutines.launch
import me.vripper.gui.components.EmptyState
import me.vripper.gui.components.Shared
import me.vripper.gui.components.fragments.ThreadSelectionTableFragment
import me.vripper.gui.controller.ThreadController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.utils.openLink
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import me.vripper.gui.fx.*

class ThreadTableView : View() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val threadController: ThreadController by inject()
    private val widgetsController: WidgetsController by inject()
    private val mainView: MainView by inject()
    private val tableView: TableView<ThreadModel>
    private val searchInput = Shared.threadsSearchInput
    private val items: ObservableList<ThreadModel> = FXCollections.observableArrayList()
    private val filteredItems = SortedFilteredList(items)
    private val loadingPlaceholder = Label("Loading")
    private val connectionFailurePlaceholder = Label("Connection Failure")
    private val emptyMessage = SimpleStringProperty("No threads yet")
    private val emptyPlaceholder = EmptyState.create(Feather.MESSAGE_SQUARE, emptyMessage)

    override val root = vbox {}

    init {
        searchInput.addListener { _, _, _ -> applyThreadFilter() }
        applyThreadFilter()
        with(root) {
            hbox {
                padding = Insets(8.0)
                spacing = 8.0
                alignment = Pos.CENTER_LEFT
                button("Clear library", FontIcon.of(Feather.TRASH_2)) {
                    action { clearThreads() }
                }
            }
            tableView = tableview(filteredItems) {
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
                column("Forum", ThreadModel::forumProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.forumProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.forumProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.forum
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.forum = it as Double
                        }
                    }
                }
                column("Starter", ThreadModel::starterProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.starterProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.starterProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.starter
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.starter = it as Double
                        }
                    }
                }
                column("Last post", ThreadModel::lastPostProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.lastPostProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.lastPostProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.lastPost
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.lastPost = it as Double
                        }
                    }
                }
                column("Last poster", ThreadModel::lastPosterProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.lastPosterProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.lastPosterProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.lastPoster
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.lastPoster = it as Double
                        }
                    }
                }
                column("Replies", ThreadModel::replyCountProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.replyCountProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.replyCountProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.replyCount
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.replyCount = it as Double
                        }
                    }
                }
                column("Views", ThreadModel::viewsProperty) {
                    isVisible = widgetsController.currentSettings.threadsColumnsModel.viewsProperty.get()
                    visibleProperty().onChange {
                        widgetsController.currentSettings.threadsColumnsModel.viewsProperty.set(
                            it
                        )
                    }
                    prefWidth = widgetsController.currentSettings.threadsColumnsWidthModel.views
                    coroutineScope.launch {
                        widthProperty().asFlow().debounce(200).collect {
                            widgetsController.currentSettings.threadsColumnsWidthModel.views = it as Double
                        }
                    }
                }
            }
            tableView.maxHeight = Double.MAX_VALUE
            VBox.setVgrow(tableView, Priority.ALWAYS)
        }

        titleProperty.bind(Bindings.size(filteredItems).map {
            if (it.toLong() > 0) {
                "Thread Library (${it.toLong()})"
            } else {
                "Thread Library"
            }
        })
        tableView.placeholder = loadingPlaceholder

        threadController.newThread.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        items.add(it)
                    }
                }
            }
        }

        threadController.updateThread.let { flow ->
            coroutineScope.launch {
                flow.collect { thread ->
                    runLater {
                        val threadModel = items.find { it.threadId == thread.threadId } ?: return@runLater
                        threadModel.total = thread.total
                        threadModel.title = thread.title
                        threadModel.forum = thread.forum
                        threadModel.starter = thread.starter
                        threadModel.lastPost = thread.lastPost
                        threadModel.lastPoster = thread.lastPoster
                        threadModel.replyCount = thread.replyCount
                        threadModel.views = thread.views
                        threadModel.newestPostId = thread.newestPostId
                        threadModel.sticky = thread.sticky
                        threadModel.open = thread.open
                    }
                }
            }
        }

        threadController.deleteThread.let { flow ->
            coroutineScope.launch {
                flow.collect { threadId ->
                    runLater {
                        items.removeIf { it.threadId == threadId }
                    }
                }
            }
        }

        threadController.clearThreads.let {
            coroutineScope.launch {
                it.collect {
                    runLater {
                        items.clear()
                    }
                }
            }
        }

        coroutineScope.launch {
            GuiEventBus.events.collect {
                when (it) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        val list = threadController.findAll()
                        runLater {
                            items.clear()
                            items.addAll(list)
                            tableView.placeholder = emptyPlaceholder
                        }
                    }

                    GuiEventBus.ChangingSession -> runLater {
                        tableView.placeholder = loadingPlaceholder
                        items.clear()
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

    private fun applyThreadFilter() {
        val query = searchInput.get().orEmpty().trim()
        emptyMessage.set(
            if (query.isBlank()) "No threads in the library yet" else "No matching threads"
        )
        filteredItems.predicate = { thread ->
            matchesQuery(thread, query)
        }
    }

    private fun matchesQuery(thread: ThreadModel, query: String): Boolean =
        query.isBlank() ||
                thread.title.contains(query, ignoreCase = true) ||
                thread.link.contains(query, ignoreCase = true) ||
                thread.forum.contains(query, ignoreCase = true) ||
                thread.starter.contains(query, ignoreCase = true) ||
                thread.threadId.toString().contains(query)

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

    private fun clearThreads() {
        confirm(
            "",
            "Are you sure you want to clear the thread library?",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Clear thread library"
        ) {
            coroutineScope.launch {
                threadController.clearAll()
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