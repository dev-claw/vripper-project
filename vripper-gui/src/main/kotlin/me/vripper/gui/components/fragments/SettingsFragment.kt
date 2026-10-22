package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import me.vripper.exception.ValidationException
import me.vripper.gui.controller.SettingsController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.utils.ImageCacheManager
import me.vripper.gui.utils.WidgetSettings
import me.vripper.model.ConnectionSettings
import me.vripper.model.DownloadSettings
import me.vripper.model.SystemSettings
import me.vripper.model.ViperSettings
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*


class SettingsFragment : Fragment("Settings") {

    val downloadSettings: DownloadSettings by param()
    val connectionSettings: ConnectionSettings by param()
    val viperSettings: ViperSettings by param()
    val systemSettings: SystemSettings by param()
    val hostSettings: Map<String, Map<String, String>> by param()
    private val settingsController: SettingsController by inject()
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadSettingsFragment: DownloadSettingsFragment =
        find(mapOf(DownloadSettingsFragment::downloadSettings to downloadSettings))
    private val connectionSettingsFragment: ConnectionSettingsFragment =
        find(mapOf(ConnectionSettingsFragment::connectionSettings to connectionSettings))
    private val viperSettingsFragment: ViperSettingsFragment =
        find(mapOf(ViperSettingsFragment::viperSettings to viperSettings))
    private val systemSettingsFragment: SystemSettingsFragment =
        find(mapOf(SystemSettingsFragment::systemSettings to systemSettings))
    private val hostSettingsFragment: HostSettingsFragment =
        find(mapOf(HostSettingsFragment::hostSettings to hostSettings))
    private val postsSettingsFragment: PostsSettingsFragment = find()
    private val viewersSettingsFragment: ViewersSettingsFragment = find()
    private val appearanceSettingsFragment: AppearanceSettingsFragment = find()

    private lateinit var sectionTitle: Label
    private lateinit var sectionHost: StackPane

    private val sections = listOf(
        SettingsSection("Appearance", Feather.SUN, appearanceSettingsFragment),
        SettingsSection("Downloads", Feather.FOLDER, downloadSettingsFragment),
        SettingsSection("Connection", Feather.ACTIVITY, connectionSettingsFragment),
        SettingsSection("System", Feather.CLIPBOARD, systemSettingsFragment),
        SettingsSection("Viper", Feather.LINK_2, viperSettingsFragment),
        SettingsSection("Hosts", Feather.IMAGE, hostSettingsFragment),
        SettingsSection("Posts", Feather.GRID, postsSettingsFragment),
        SettingsSection("Viewers", Feather.EYE, viewersSettingsFragment),
    )

    override val root = borderpane {
        prefWidth = 940.0
        prefHeight = 620.0
        minWidth = 780.0
        minHeight = 480.0

        val sidebar = buildSidebar()
        val content = buildContent()
        center = HBox(sidebar, content).apply {
            HBox.setHgrow(content, Priority.ALWAYS)
        }
        bottom = buildFooter()
        showSection(sections.first())
    }

    private fun buildSidebar(): Region {
        val group = ToggleGroup()
        return VBox(4.0).apply {
            padding = Insets(10.0)
            prefWidth = 205.0
            minWidth = 185.0
            maxWidth = 230.0
            addClass(Styles.BG_SUBTLE)
            addClass(Styles.BORDER_MUTED)
            style = "-fx-border-width: 0 1 0 0;"

            sections.forEachIndexed { index, section ->
                children.add(ToggleButton(section.title).apply {
                    toggleGroup = group
                    graphic = FontIcon.of(section.icon).apply {
                        styleClass.add("vripper-icon-nav")
                    }
                    graphicTextGap = 10.0
                    alignment = Pos.CENTER_LEFT
                    maxWidth = Double.MAX_VALUE
                    padding = Insets(9.0, 10.0, 9.0, 10.0)
                    addClass(Styles.FLAT)
                    isSelected = index == 0
                    setOnAction {
                        isSelected = true
                        showSection(section)
                    }
                })
            }
        }
    }

    private fun buildContent(): Region {
        sectionTitle = Label().apply {
            addClass(Styles.TITLE_3)
        }
        sectionHost = StackPane().apply {
            alignment = Pos.TOP_LEFT
            padding = Insets(0.0, 12.0, 8.0, 0.0)
        }
        val scroll = ScrollPane(sectionHost).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            style = "-fx-background-color: transparent; -fx-border-width: 0;"
        }
        VBox.setVgrow(scroll, Priority.ALWAYS)
        return VBox(12.0, sectionTitle, scroll).apply {
            padding = Insets(18.0, 18.0, 8.0, 18.0)
        }
    }

    private fun buildFooter(): Region = hbox {
        alignment = Pos.CENTER_RIGHT
        padding = insets(12.0, 18.0, 12.0, 18.0)
        addClass(Styles.BORDER_MUTED)
        style = "-fx-border-width: 1 0 0 0;"

        button("Save") {
            graphic = FontIcon.of(Feather.SAVE)
            addClass(Styles.ACCENT)
            isDefaultButton = true
            action {
                coroutineScope.launch {
                    try {
                        settingsController.saveNewSettings(
                            downloadSettingsFragment.downloadSettingsModel,
                            connectionSettingsFragment.connectionSettingsModel,
                            viperSettingsFragment.viperSettingsModel,
                            systemSettingsFragment.systemSettingsModel,
                            hostSettingsFragment.hostSettingsModels
                        )
                        runLater {
                            commitPostsSettings()
                            commitImageViewerSettings()
                            commitOverviewWindowSettings()
                            close()
                        }
                    } catch (e: ValidationException) {
                        alert(Alert.AlertType.ERROR, "Invalid settings", e.message)
                    }
                }
            }
        }
    }

    private fun showSection(section: SettingsSection) {
        sectionTitle.text = section.title
        sectionHost.children.setAll(section.fragment.root)
    }

    /**
     * Commits the posts settings edited in the Posts section. These are staged
     * while the dialog is open and only applied after a successful save.
     */
    private fun commitPostsSettings() {
        val staged = postsSettingsFragment.postsSettingsModel
        val target = widgetsController.currentSettings
        target.postsViewMode = staged.postsViewMode
        target.postsGridTileSize = staged.postsGridTileSize
        target.postsPreviewPopup = staged.postsPreviewPopup
    }

    /**
     * Commits the image viewer settings edited in the Viewers section. Unlike
     * the other client-side widget settings (which persist on change), these
     * follow the dialog's Save button, so they are applied here only after a
     * successful save. Must run on the FX thread (it updates bound properties).
     */
    private fun commitImageViewerSettings() {
        val staged = viewersSettingsFragment.imageViewerSettingsModel
        val target = widgetsController.currentSettings.imageViewerSettingsModel
        target.decodedCacheMb = staged.decodedCacheMb
        target.bytePreloadRadius = staged.bytePreloadRadius
        target.bytePreloadConcurrency = staged.bytePreloadConcurrency
        target.decodePreloadConcurrency = staged.decodePreloadConcurrency
        target.diskCacheMb = staged.diskCacheMb
        target.fadeAnimation = staged.fadeAnimation
        WidgetSettings.update(widgetsController.currentSettings)
        ImageCacheManager.resize(staged.diskCacheMb.toLong() * 1024 * 1024)
    }

    /**
     * Commits the overview window settings edited in the Viewers section.
     * Applies to overview windows opened after the dialog is saved.
     */
    private fun commitOverviewWindowSettings() {
        val staged = viewersSettingsFragment.overviewWindowSettingsModel
        val target = widgetsController.currentSettings.workspaceSettingsModel
        target.browseLayout = staged.browseLayout
        target.detailsPanelVisible = staged.detailsPanelVisible
        target.detailsPanelDividerPosition = staged.detailsPanelDividerPosition
        target.overviewGridTileSize = staged.overviewGridTileSize
        target.windowWidth = staged.windowWidth
        target.windowHeight = staged.windowHeight
        target.windowX = staged.windowX
        target.windowY = staged.windowY
        target.windowMaximized = staged.windowMaximized
        WidgetSettings.update(widgetsController.currentSettings)
    }

    override fun onUndock() {
        coroutineScope.cancel()
    }

    private data class SettingsSection(
        val title: String,
        val icon: Feather,
        val fragment: Fragment
    )
}
