package me.vripper.gui.components.fragments

import atlantafx.base.controls.ToggleSwitch
import javafx.collections.FXCollections
import javafx.scene.control.Spinner
import kotlinx.coroutines.runBlocking
import me.vripper.gui.controller.SettingsController
import me.vripper.gui.model.settings.ViperSettingsModel
import me.vripper.model.ViperSettings
import tornadofx.*

class ViperSettingsFragment : Fragment("Viper Settings") {

    val viperSettings: ViperSettings by param()
    private val settingsController: SettingsController by inject()
    private val proxies = FXCollections.observableArrayList<String>()
    val viperSettingsModel = ViperSettingsModel()

    override val root = vbox {}

    init {
        viperSettingsModel.username = viperSettings.username
        viperSettingsModel.password = viperSettings.password
        viperSettingsModel.thanks = viperSettings.thanks
        viperSettingsModel.host = viperSettings.host
        viperSettingsModel.requestLimit = viperSettings.requestLimit
        viperSettingsModel.fetchMetadata = viperSettings.fetchMetadata
        proxies.addAll(runBlocking { settingsController.getProxies() })
        with(root) {
            form {
                fieldset {
                    field("Viper domain") {
                        combobox(viperSettingsModel.hostProperty, proxies)
                    }
                    field("Rate limit (requests/s)") {
                        add(Spinner<Int>(1, 5, viperSettingsModel.requestLimit.toInt()).apply {
                            valueProperty().onChange {
                                viperSettingsModel.requestLimit = it!!.toLong()
                            }
                            isEditable = true
                            atlantafx.base.util.IntegerStringConverter.createFor(this)
                        })
                    }
                    field("Fetch Metadata") {
                        checkbox {
                            bind(viperSettingsModel.fetchMetadataProperty)
                        }
                    }
                    field("Authentication") {
                        add(ToggleSwitch().apply {
                            isSelected = viperSettings.login
                            viperSettingsModel.loginProperty.bind(selectedProperty())
                        })
                    }
                    fieldset {
                        visibleWhen(viperSettingsModel.loginProperty)
                        field("Username") {
                            textfield(viperSettingsModel.usernameProperty)
                        }
                        field("Password") {
                            passwordfield(viperSettingsModel.passwordProperty)
                        }
                        field("Leave likes") {
                            checkbox {
                                bind(viperSettingsModel.thanksProperty)
                            }
                        }
                    }
                }
            }
        }
    }
}