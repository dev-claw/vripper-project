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
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*


class SettingsFragment : Fragment("Settings") {

    private val settingsController: SettingsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadSettingsFragment: DownloadSettingsFragment = find()
    private val connectionSettingsFragment: ConnectionSettingsFragment = find()
    private val viperSettingsFragment: ViperSettingsFragment = find()
    private val systemSettingsFragment: SystemSettingsFragment = find()

    override val root = vbox(alignment = Pos.CENTER_RIGHT) {
        spacing = 5.0
        tabpane {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            VBox.setVgrow(this, Priority.ALWAYS)
            minWidth = 100.0
            minHeight = 100.0
            prefHeight = 400.0
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
                                    systemSettingsFragment.systemSettingsModel
                                )
                                runLater {
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

    override fun onUndock() {
        coroutineScope.cancel()
    }
}