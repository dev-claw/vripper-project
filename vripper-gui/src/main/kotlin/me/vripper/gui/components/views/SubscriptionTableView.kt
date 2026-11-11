package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.gui.components.EmptyState
import me.vripper.gui.components.Shared
import me.vripper.gui.components.fragments.ThreadSelectionTableFragment
import me.vripper.gui.controller.SubscriptionController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.utils.openLink
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class SubscriptionTableView : View("Subscriptions") {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subscriptionController: SubscriptionController by inject()
    private val mainView: MainView by inject()
    private val tableView: TableView<ThreadModel>
    private val searchInput = Shared.subscriptionsSearchInput
    private val items: ObservableList<ThreadModel> = FXCollections.observableArrayList()
    private val filteredItems = SortedFilteredList(items)
    private val loadingPlaceholder = Label("Loading")
    private val connectionFailurePlaceholder = Label("Connection Failure")
    private val emptyMessage = SimpleStringProperty("No subscriptions yet")
    private val emptyPlaceholder = EmptyState.create(Feather.RSS, emptyMessage)

    override val root = vbox {}

    init {
        searchInput.addListener { _, _, _ -> applySubscriptionFilter() }
        applySubscriptionFilter()
        with(root) {
            hbox {
                padding = Insets(8.0)
                spacing = 8.0
                alignment = Pos.CENTER_LEFT
                button("Refresh subscriptions", FontIcon.of(Feather.REFRESH_CW)) {
                    action { refreshSubscriptions() }
                }
                button("Clear subscriptions", FontIcon.of(Feather.TRASH_2)) {
                    action { clearSubscriptions() }
                }
                spacer()
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
                        setOnAction { selectPosts(tableRow.item.threadId) }
                        graphic = FontIcon.of(Feather.MENU)
                    }
                    val urlItem = MenuItem("Open link").apply {
                        setOnAction { openLink(tableRow.item.link) }
                        graphic = FontIcon.of(Feather.LINK)
                    }
                    val contextMenu = ContextMenu(selectItem, urlItem)
                    tableRow.contextMenuProperty()
                        .bind(tableRow.emptyProperty().map { empty -> if (empty) null else contextMenu })
                    tableRow
                }
                column("Title", ThreadModel::titleProperty) { prefWidth = 360.0 }
                column("Count", ThreadModel::totalProperty) { prefWidth = 70.0 }
                column("Forum", ThreadModel::forumProperty) { prefWidth = 160.0 }
                column("Starter", ThreadModel::starterProperty) { prefWidth = 140.0 }
                column("Last post", ThreadModel::lastPostProperty) { prefWidth = 140.0 }
                column("Last poster", ThreadModel::lastPosterProperty) { prefWidth = 140.0 }
                column("Replies", ThreadModel::replyCountProperty) { prefWidth = 80.0 }
                column("Views", ThreadModel::viewsProperty) { prefWidth = 80.0 }
            }
            tableView.maxHeight = Double.MAX_VALUE
            VBox.setVgrow(tableView, Priority.ALWAYS)
        }

        titleProperty.bind(filteredItems.sizeProperty.map {
            if (it.toLong() > 0) "Subscriptions (${it.toLong()})" else "Subscriptions"
        })
        tableView.placeholder = loadingPlaceholder

        subscriptionController.newSubscription.let { flow ->
            coroutineScope.launch {
                flow.collect { runLater { items.add(it) } }
            }
        }

        subscriptionController.updateSubscription.let { flow ->
            coroutineScope.launch {
                flow.collect { subscription ->
                    runLater {
                        val model = items.find { it.threadId == subscription.threadId } ?: return@runLater
                        model.total = subscription.total
                        model.title = subscription.title
                        model.forum = subscription.forum
                        model.starter = subscription.starter
                        model.lastPost = subscription.lastPost
                        model.lastPoster = subscription.lastPoster
                        model.replyCount = subscription.replyCount
                        model.views = subscription.views
                        model.newestPostId = subscription.newestPostId
                        model.sticky = subscription.sticky
                        model.open = subscription.open
                    }
                }
            }
        }

        subscriptionController.deleteSubscription.let { flow ->
            coroutineScope.launch {
                flow.collect { threadId -> runLater { items.removeIf { it.threadId == threadId } } }
            }
        }

        subscriptionController.clearSubscriptions.let { flow ->
            coroutineScope.launch {
                flow.collect { runLater { items.clear() } }
            }
        }

        coroutineScope.launch {
            GuiEventBus.events.collect {
                when (it) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        val list = subscriptionController.findAll()
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

    private fun applySubscriptionFilter() {
        val query = searchInput.get().orEmpty().trim()
        emptyMessage.set(if (query.isBlank()) "No subscriptions yet" else "No matching subscriptions")
        filteredItems.predicate = { subscription ->
            query.isBlank() ||
                    subscription.title.contains(query, ignoreCase = true) ||
                    subscription.link.contains(query, ignoreCase = true) ||
                    subscription.forum.contains(query, ignoreCase = true) ||
                    subscription.starter.contains(query, ignoreCase = true) ||
                    subscription.lastPoster.contains(query, ignoreCase = true) ||
                    subscription.threadId.toString().contains(query)
        }
    }

    private fun isCurrentTab(): Boolean = mainView.root.selectionModel.selectedItem.id == "subscription-tab"

    private fun deleteSelected() {
        val threadIdList = tableView.selectionModel.selectedItems.map { it.threadId }
        confirm(
            "",
            "Confirm removal of ${threadIdList.size} subscription${if (threadIdList.size > 1) "s" else ""}?",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Remove subscriptions"
        ) {
            coroutineScope.launch { subscriptionController.delete(threadIdList) }
        }
    }

    private fun refreshSubscriptions() {
        coroutineScope.launch { subscriptionController.refresh() }
    }

    private fun clearSubscriptions() {
        confirm(
            "",
            "Are you sure you want to clear all subscriptions?",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Clear subscriptions"
        ) {
            coroutineScope.launch { subscriptionController.clearAll() }
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
