package me.vripper.gui.components.views

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.geometry.Orientation
import javafx.scene.control.ButtonType
import javafx.scene.control.ContentDisplay
import javafx.scene.layout.Priority
import kotlinx.coroutines.*
import me.vripper.gui.components.Shared
import me.vripper.gui.components.fragments.AddLinksFragment
import me.vripper.gui.components.fragments.SettingsFragment
import me.vripper.gui.controller.ActionBarController
import me.vripper.gui.controller.PostController
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class ActionBarView : View() {
    private val downloadActiveProperty = SimpleBooleanProperty(true)
    private val postController: PostController by inject()
    private val postsTableView: PostsTableView by inject()
    private val actionBarController: ActionBarController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = SimpleIntegerProperty(0)

    override val root = toolbar {}

    init {
        with(root) {
            id = "action_toolbar"
            padding = insets(all = 5)
            button("Add links", FontIcon.of(Feather.PLUS)) {
                contentDisplay = ContentDisplay.GRAPHIC_ONLY
                tooltip("Add links [Ctrl+L]")
                action {
                    find<AddLinksFragment>().apply {
                        input.clear()
                    }.openModal()?.apply {
                        minWidth = 100.0
                        minHeight = 100.0
                    }
                }
            }
            separator(Orientation.VERTICAL)

            button("Start All", FontIcon.of(Feather.SKIP_FORWARD)) {
                contentDisplay = ContentDisplay.GRAPHIC_ONLY
                tooltip("Start downloads [Ctrl+S]")
                disableWhen(downloadActiveProperty)
                action {
                    coroutineScope.launch {
                        postController.startAll()
                    }
                }
            }
            button("Stop All", FontIcon.of(Feather.SQUARE)) {
                contentDisplay = ContentDisplay.GRAPHIC_ONLY
                tooltip("Stops all running downloads [Ctrl+Q]")
                disableWhen(downloadActiveProperty.not())
                action {
                    coroutineScope.launch {
                        postController.stopAll()
                    }
                }
            }
            button("Clear", FontIcon.of(Feather.TRASH_2)) {
                contentDisplay = ContentDisplay.GRAPHIC_ONLY
                tooltip("Clear all finished downloads [Ctrl+Del]")
                action {
                    confirm(
                        "",
                        "Confirm removal of finished posts?",
                        ButtonType.YES,
                        ButtonType.NO,
                        owner = primaryStage,
                        title = "Clean finished posts"
                    ) {
                        coroutineScope.launch {
                            val clearPosts = async { postController.clearPosts() }.await()
                            runLater {
                                postsTableView.tableView.items.removeIf { clearPosts.contains(it.postId) }
                            }
                        }
                    }
                }
            }
            separator(Orientation.VERTICAL)
            button("Settings", FontIcon.of(Feather.SETTINGS)) {
                contentDisplay = ContentDisplay.GRAPHIC_ONLY
                tooltip("Open settings menu [Ctrl+P]")
                action {
                    find<SettingsFragment>().openModal()?.apply {
                        minWidth = 100.0
                        minHeight = 100.0
                    }
                }
            }
            separator(Orientation.VERTICAL)
            textfield(Shared.searchInput) {
                promptText = "Search"
                hgrow = Priority.ALWAYS
            }
        }
        downloadActiveProperty.bind(running.greaterThan(0))

        coroutineScope.launch {
            actionBarController.onQueueStateUpdate.collect {
                runLater {
                    running.set(it.running)
                }
            }
        }
    }
}