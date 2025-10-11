package me.vripper.gui.components.views

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.ButtonType
import javafx.scene.control.ToggleGroup
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import kotlinx.coroutines.*
import me.vripper.gui.VripperGuiApplication
import me.vripper.gui.components.fragments.AboutFragment
import me.vripper.gui.components.fragments.AddLinksFragment
import me.vripper.gui.components.fragments.SessionFragment
import me.vripper.gui.components.fragments.SettingsFragment
import me.vripper.gui.controller.ActionBarController
import me.vripper.gui.controller.PostController
import me.vripper.gui.controller.SettingsController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.utils.openLink
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.ApplicationProperties
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class MenuBarView : View() {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadActiveProperty = SimpleBooleanProperty(false)
    private val postsTableView: PostsTableView by inject()
    private val widgetsController: WidgetsController by inject()
    private val postController: PostController by inject()
    private val actionBarController: ActionBarController by inject()
    private val settingsController: SettingsController by inject()
    private lateinit var appEndpointService: IAppEndpointService
    private val running = SimpleIntegerProperty(0)

    override val root = menubar {}

    init {
        with(root) {
            menu("File") {
                item("Add links", KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN)).apply {
                    graphic = FontIcon.of(Feather.PLUS)
                    action {
                        find<AddLinksFragment>().apply {
                            input.clear()
                        }.openModal()?.apply {
                            minWidth = 100.0
                            minHeight = 100.0
                        }
                    }
                }
                separator()
                item("Start All", KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)).apply {
                    graphic = FontIcon.of(Feather.SKIP_FORWARD)
                    disableWhen(downloadActiveProperty)
                    action {
                        coroutineScope.launch {
                            postController.startAll()
                        }
                    }
                }
                item("Stop All", KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN)).apply {
                    graphic = FontIcon.of(Feather.SQUARE)
                    disableWhen(downloadActiveProperty.not())
                    action {
                        coroutineScope.launch {
                            postController.stopAll()
                        }
                    }
                }
                item("Clear", KeyCodeCombination(KeyCode.DELETE, KeyCombination.CONTROL_DOWN)).apply {
                    graphic = FontIcon.of(Feather.TRASH_2)
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
                                    postsTableView.tableView.items.removeIf { clearPosts.contains(it.vgPostId) }
                                }
                            }
                        }
                    }
                }
                separator()
                item("Settings", KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN)).apply {
                    graphic = FontIcon.of(Feather.SETTINGS)
                    action {
                        coroutineScope.launch {
                            val downloadSettings = settingsController.findDownloadSettings()
                            val connectionSettings = settingsController.findConnectionSettings()
                            val viperGirlsSettings = settingsController.findViperGirlsSettings()
                            val systemSettings = settingsController.findSystemSettings()
                            runLater {
                                find<SettingsFragment>(
                                    mapOf(
                                        SettingsFragment::downloadSettings to downloadSettings,
                                        SettingsFragment::connectionSettings to connectionSettings,
                                        SettingsFragment::viperSettings to viperGirlsSettings,
                                        SettingsFragment::systemSettings to systemSettings,
                                    )
                                ).openModal()
                            }
                        }
                    }
                }
                separator()
                item("Change session", KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN)) {
                    graphic = FontIcon.of(Feather.LINK_2)
                    action {
                        find<SessionFragment>().openModal()?.apply {
                            minWidth = 100.0
                            minHeight = 100.0
                        }
                    }
                }
                separator()
                item("Exit", KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN)).apply {
                    graphic = FontIcon.of(Feather.X_SQUARE)
                    action {
                        VripperGuiApplication.APP_INSTANCE.stop()
                    }
                }
            }
            menu("View") {
                checkmenuitem(
                    "Toolbar", KeyCodeCombination(KeyCode.F6)
                ).bind(widgetsController.currentSettings.visibleToolbarPanelProperty)
                checkmenuitem(
                    "Info Panel", KeyCodeCombination(KeyCode.F7)
                ).bind(widgetsController.currentSettings.visibleInfoPanelProperty)
                checkmenuitem(
                    "Status Bar", KeyCodeCombination(KeyCode.F8)
                ).bind(widgetsController.currentSettings.visibleStatusBarPanelProperty)
                menu("Theme") {
                    val toggleGroup = ToggleGroup()

                    val cupertinoLight = radiomenuitem("Cupertino Light", toggleGroup).apply {
                        this.selectedProperty().onChange {
                            widgetsController.currentSettings.theme = "CupertinoLight"
                            widgetsController.currentSettings.darkMode = false

                        }
                    }
                    val cupertinoDark = radiomenuitem("Cupertino Dark", toggleGroup).apply {
                        this.selectedProperty().onChange {
                            widgetsController.currentSettings.theme = "CupertinoDark"
                            widgetsController.currentSettings.darkMode = true
                        }
                    }
                    val nordLight = radiomenuitem("Nord Light", toggleGroup).apply {
                        this.selectedProperty().onChange {
                            widgetsController.currentSettings.theme = "NordLight"
                            widgetsController.currentSettings.darkMode = false
                        }
                    }
                    val nordDark = radiomenuitem("Nord Dark", toggleGroup).apply {
                        this.selectedProperty().onChange {
                            widgetsController.currentSettings.theme = "NordDark"
                            widgetsController.currentSettings.darkMode = true
                        }
                    }
                    val primerLight = radiomenuitem("Primer Light", toggleGroup).apply {
                        this.selectedProperty().onChange {
                            widgetsController.currentSettings.theme = "PrimerLight"
                            widgetsController.currentSettings.darkMode = false
                        }
                    }
                    val primerDark = radiomenuitem("Primer Dark", toggleGroup).apply {
                        this.selectedProperty().onChange {
                            widgetsController.currentSettings.theme = "PrimerDark"
                            widgetsController.currentSettings.darkMode = true
                        }
                    }
                    val dracula = radiomenuitem("Dracula", toggleGroup).apply {
                        this.selectedProperty().onChange {
                            widgetsController.currentSettings.theme = "Dracula"
                            widgetsController.currentSettings.darkMode = true
                        }
                    }

                    when (widgetsController.currentSettings.theme) {
                        "CupertinoLight" -> cupertinoLight.isSelected = true
                        "CupertinoDark" -> cupertinoDark.isSelected = true
                        "NordLight" -> nordLight.isSelected = true
                        "NordDark" -> nordDark.isSelected = true
                        "PrimerLight" -> primerLight.isSelected = true
                        "PrimerDark" -> primerDark.isSelected = true
                        "Dracula" -> dracula.isSelected = true
                        else -> cupertinoLight.isSelected = true
                    }
                }
            }
            menu("Help") {
                item("Database migration").apply {
                    graphic = FontIcon.of(Feather.DATABASE)
                    action {
                        confirm(
                            "",
                            "Do you want to import your data from the previous 5.x version?",
                            ButtonType.YES,
                            ButtonType.NO,
                            owner = primaryStage,
                            title = "Database Migration"
                        ) {
                            coroutineScope.launch {
                                val message =
                                    runCatching { appEndpointService.dbMigration() }.getOrDefault("Migration failed")
                                runLater {
                                    information(
                                        header = "",
                                        content = message,
                                        title = "Database migration",
                                        owner = primaryStage,
                                    )
                                }
                            }
                        }

                    }
                }
                separator()
                item("Check for updates").apply {
                    graphic = FontIcon.of(Feather.REFRESH_CCW)
                    action {
                        val latestVersion = ApplicationProperties.latestVersion()
                        val currentVersion = ApplicationProperties.VERSION

                        if (latestVersion > currentVersion) {
                            information(
                                header = "",
                                content = "A newer version of VRipper is available \nLatest version is $latestVersion\nDo you want to go to the release page?",
                                title = "VRipper updates",
                                buttons = arrayOf(ButtonType.YES, ButtonType.NO),
                                owner = primaryStage,
                            ) {
                                if (it == ButtonType.YES) {
                                    openLink("https://github.com/dev-claw/vripper-project/releases/tag/$latestVersion")
                                }
                            }
                        } else {
                            information(
                                header = "",
                                content = "You are running the latest version of VRipper.",
                                title = "VRipper updates",
                                owner = primaryStage
                            )
                        }
                    }
                }
                separator()
                item("About").apply {
                    graphic = FontIcon.of(Feather.INFO)
                    action {
                        find<AboutFragment>().openModal()?.apply {
                            this.minWidth = 100.0
                            this.minHeight = 100.0
                        }
                    }
                }
            }
        }
        downloadActiveProperty.bind(running.greaterThan(0))

        coroutineScope.launch {
            var job: Job? = null
            GuiEventBus.events.collect { event ->
                when (event) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        job = launch {
                            actionBarController.onQueueStateUpdate.collect {
                                runLater {
                                    running.set(it.running)
                                }
                            }
                        }
                    }

                    GuiEventBus.ChangingSession -> {
                        job?.cancelAndJoin()
                        runLater {
                            running.set(0)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}