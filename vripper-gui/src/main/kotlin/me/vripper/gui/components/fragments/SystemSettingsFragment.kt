package me.vripper.gui.components.fragments

import atlantafx.base.controls.ToggleSwitch
import atlantafx.base.util.IntegerStringConverter
import javafx.scene.control.Spinner
import kotlinx.coroutines.*
import me.vripper.gui.controller.SettingsController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.settings.SystemSettingsModel
import me.vripper.model.SystemSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tornadofx.*

class SystemSettingsFragment : Fragment("System Settings"), KoinComponent {
    private val settingsController: SettingsController by inject()
    private val widgetsController: WidgetsController by inject<WidgetsController>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var systemSettings: SystemSettings
    val systemSettingsModel = SystemSettingsModel()

    override val root = vbox {}

    init {
        coroutineScope.launch {
            systemSettings = settingsController.findSystemSettings()
            systemSettingsModel.tempPath = systemSettings.tempPath
            runLater {
                with(root) {
                    form {
                        fieldset {
                            field("Temporary Path") {
                                textfield(systemSettingsModel.tempPathProperty) {
                                    editableWhen(widgetsController.currentSettings.localSessionProperty.not())
                                }
                                button("Browse") {
                                    visibleWhen(widgetsController.currentSettings.localSessionProperty)
                                    action {
                                        val directory = chooseDirectory(title = "Select temporary folder")
                                        if (directory != null) {
                                            systemSettingsModel.tempPathProperty.set(directory.path)
                                        }
                                    }
                                }
                            }
                            field("Max log entries") {
                                add(Spinner<Int>(10, 10000, systemSettings.maxEventLog).apply {
                                    systemSettingsModel.logEntriesProperty.bind(valueProperty())
                                    isEditable = true
                                    IntegerStringConverter.createFor(this)
                                })
                            }
                            fieldset {
                                field("Clipboard monitoring") {
                                    add(ToggleSwitch().apply {
                                        isSelected = systemSettings.enableClipboardMonitoring
                                        systemSettingsModel.enableProperty.bind(selectedProperty())
                                    })
                                }
                                fieldset {
                                    visibleWhen(systemSettingsModel.enableProperty)
                                    field("Polling rate (ms)") {
                                        add(
                                            Spinner<Int>(
                                                500,
                                                kotlin.Int.MAX_VALUE,
                                                systemSettings.clipboardPollingRate
                                            ).apply {
                                                systemSettingsModel.pollingRateProperty.bind(valueProperty())
                                                isEditable = true
                                                IntegerStringConverter.createFor(this)
                                            })
                                    }
                                }
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