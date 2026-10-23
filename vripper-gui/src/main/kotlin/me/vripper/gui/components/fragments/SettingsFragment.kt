package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.TabPane
import javafx.scene.layout.Priority
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
    private val imageViewerSettingsFragment: ImageViewerSettingsFragment = find()
    private val workspaceSettingsFragment: WorkspaceSettingsFragment = find()


    override val root = vbox(alignment = Pos.CENTER_RIGHT) {
        spacing = 5.0
        tabpane {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            VBox.setVgrow(this, Priority.ALWAYS)
            minWidth = 100.0
            minHeight = 100.0
            prefHeight = 400.0
            prefWidth = 800.0
            tab(downloadSettingsFragment.title) {
                add(downloadSettingsFragment)
                graphic = FontIcon.of(Feather.FOLDER)
            }
            tab(connectionSettingsFragment.title) {
                add(connectionSettingsFragment)
                graphic = FontIcon.of(Feather.ACTIVITY)
            }
            tab(systemSettingsFragment.title) {
                add(systemSettingsFragment)
                graphic = FontIcon.of(Feather.CLIPBOARD)
            }
            tab(viperSettingsFragment.title) {
                add(viperSettingsFragment)
                graphic = FontIcon.of(Feather.LINK_2)
            }
            tab(hostSettingsFragment.title) {
                add(hostSettingsFragment)
                graphic = FontIcon.of(Feather.IMAGE)
            }
            tab(imageViewerSettingsFragment.title) {
                add(imageViewerSettingsFragment)
                graphic = FontIcon.of(Feather.EYE)
            }
            tab(workspaceSettingsFragment.title) {
                add(workspaceSettingsFragment)
                graphic = FontIcon.of(Feather.GRID)
            }
        }
        borderpane {
            right {
                padding = insets(all = 5.0)
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
                                    commitImageViewerSettings()
                                    commitWorkspaceSettings()
                                    close()
                                }
                            } catch (e: ValidationException) {
                                alert(Alert.AlertType.ERROR, "Invalid settings", e.message)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Commits the image viewer settings edited in the dedicated tab. Unlike the
     * other client-side widget settings (which persist on change), these follow
     * the dialog's Save button, so they are applied here only after a successful
     * save. Must run on the FX thread (it updates bound properties).
     */
    private fun commitImageViewerSettings() {
        val staged = imageViewerSettingsFragment.imageViewerSettingsModel
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
     * Commits the workspace settings edited in the dedicated tab. Applies to
     * workspaces opened after the dialog is saved.
     */
    private fun commitWorkspaceSettings() {
        val staged = workspaceSettingsFragment.workspaceSettingsModel
        val target = widgetsController.currentSettings.workspaceSettingsModel
        target.browseLayout = staged.browseLayout
        target.detailsPanelVisible = staged.detailsPanelVisible
        target.detailsPanelDividerPosition = staged.detailsPanelDividerPosition
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
}