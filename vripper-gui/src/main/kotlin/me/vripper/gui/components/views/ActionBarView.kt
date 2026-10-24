package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.StringProperty
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.ButtonType
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import kotlinx.coroutines.*
import me.vripper.gui.components.Shared
import me.vripper.gui.components.fragments.AddLinksFragment
import me.vripper.gui.components.fragments.SettingsFragment
import me.vripper.gui.controller.ActionBarController
import me.vripper.gui.controller.PostController
import me.vripper.gui.controller.PostsTabController
import me.vripper.gui.controller.SettingsController
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class ActionBarView : View() {
    private val downloadActiveProperty = SimpleBooleanProperty(true)
    private val postController: PostController by inject()
    private val postsController: PostsTabController by inject()
    private val actionBarController: ActionBarController by inject()
    private val settingsController: SettingsController by inject()
    private val mainView: MainView by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = SimpleIntegerProperty(0)

    private lateinit var searchField: TextField
    private var activeSearchProperty: StringProperty? = null

    override val root = toolbar {}

    init {
        with(root) {
            this.id = "action_toolbar"
            padding = insets(top = 5, right = 0, bottom = 5, left = 5)

            add(buildBrand())
            separator(Orientation.VERTICAL)

            searchField = TextField().apply {
                promptText = "Search posts"
                minWidth = 220.0
                prefWidth = 320.0
                maxWidth = Double.MAX_VALUE
                HBox.setHgrow(this, Priority.ALWAYS)
                tooltip("Filter posts by title, ID, host, status or path")
            }
            add(searchField)
            separator(Orientation.VERTICAL)

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
                                postsController.removePostsByVgPostId(clearPosts)
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
                    coroutineScope.launch {
                        val downloadSettings = settingsController.findDownloadSettings()
                        val connectionSettings = settingsController.findConnectionSettings()
                        val viperGirlsSettings = settingsController.findViperGirlsSettings()
                        val systemSettings = settingsController.findSystemSettings()
                        val hostSettings = settingsController.findHostSettings()
                        runLater {
                            find<SettingsFragment>(
                                mapOf(
                                    SettingsFragment::downloadSettings to downloadSettings,
                                    SettingsFragment::connectionSettings to connectionSettings,
                                    SettingsFragment::viperSettings to viperGirlsSettings,
                                    SettingsFragment::systemSettings to systemSettings,
                                    SettingsFragment::hostSettings to hostSettings,
                                )
                            ).openModal()
                        }
                    }

                }
            }

        }

        mainView.root.selectionModel.selectedItemProperty().onChange { updateContext() }
        updateContext()

        downloadActiveProperty.bind(running.greaterThan(0))

        coroutineScope.launch {
            actionBarController.onQueueStateUpdate.collect {
                runLater {
                    running.set(it.running)
                }
            }
        }
    }

    private fun buildBrand(): HBox = HBox(6.0).apply {
        alignment = Pos.CENTER_LEFT
        children.add(ImageView(Image("icons/32x32.png")).apply {
            fitWidth = 22.0
            fitHeight = 22.0
            isPreserveRatio = true
        })
        children.add(Label("VRipper").apply {
            addClass(Styles.TEXT_BOLD)
            style = "-fx-font-size: 1.05em;"
        })
    }

    private fun updateContext() {
        val tabId = mainView.root.selectionModel.selectedItem?.id ?: "download-tab"
        val target = when (tabId) {
            "thread-tab" -> SearchTarget(
                Shared.threadsSearchInput,
                "Search threads",
                "Filter threads by title, URL or ID",
            )
            "task-tab" -> SearchTarget(
                Shared.tasksSearchInput,
                "Search tasks",
                "Filter tasks by name, description, category or status",
            )
            "log-tab" -> SearchTarget(
                Shared.logsSearchInput,
                "Search logs",
                "Filter logs by message, logger, thread or level",
            )
            else -> SearchTarget(
                Shared.postsSearchInput,
                "Search posts",
                "Filter posts by title, ID, host, status or path",
            )
        }

        if (activeSearchProperty !== target.property) {
            activeSearchProperty?.let { searchField.textProperty().unbindBidirectional(it) }
            searchField.text = target.property.get()
            searchField.textProperty().bindBidirectional(target.property)
            activeSearchProperty = target.property
        }
        searchField.promptText = target.prompt
        searchField.tooltip = Tooltip(target.tooltip)
    }

    private data class SearchTarget(
        val property: StringProperty,
        val prompt: String,
        val tooltip: String,
    )
}
