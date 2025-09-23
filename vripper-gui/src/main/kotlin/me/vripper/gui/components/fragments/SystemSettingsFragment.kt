package me.vripper.gui.components.fragments

import atlantafx.base.controls.ToggleSwitch
import atlantafx.base.util.IntegerStringConverter
import javafx.scene.control.Spinner
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.settings.SystemSettingsModel
import me.vripper.model.SystemSettings
import tornadofx.*

class SystemSettingsFragment : Fragment("System Settings") {

    val systemSettings: SystemSettings by param()
    private val widgetsController: WidgetsController by inject()
    val systemSettingsModel = SystemSettingsModel()

    override val root = vbox {}

    init {
        systemSettingsModel.tempPath = systemSettings.tempPath
        systemSettingsModel.logEntries = systemSettings.maxEventLog
        systemSettingsModel.enable = systemSettings.enableClipboardMonitoring
        systemSettingsModel.pollingRate = systemSettings.clipboardPollingRate
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
                        add(Spinner<Int>(10, 10000, systemSettingsModel.logEntries).apply {
                            valueProperty().onChange {
                                systemSettingsModel.logEntries = it!!
                            }
                            isEditable = true
                            IntegerStringConverter.createFor(this)
                        })
                    }
                    fieldset {
                        field("Clipboard monitoring") {
                            add(ToggleSwitch().apply {
                                isSelected = systemSettingsModel.enable
                                selectedProperty().onChange {
                                    systemSettingsModel.enable = it
                                }
                            })
                        }
                        fieldset {
                            visibleWhen(systemSettingsModel.enableProperty)
                            field("Polling rate (ms)") {
                                add(
                                    Spinner<Int>(
                                        500,
                                        Int.MAX_VALUE,
                                        systemSettingsModel.pollingRate
                                    ).apply {
                                        valueProperty().onChange {
                                            systemSettingsModel.pollingRate = it!!
                                        }
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