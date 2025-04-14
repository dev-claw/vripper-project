package me.vripper.gui.components.fragments

import atlantafx.base.controls.ToggleSwitch
import javafx.collections.FXCollections
import javafx.scene.control.Spinner
import kotlinx.coroutines.*
import me.vripper.gui.controller.SettingsController
import me.vripper.gui.model.settings.ViperSettingsModel
import me.vripper.model.ViperSettings
import tornadofx.*

class ViperSettingsFragment : Fragment("Viper Settings") {
    private val settingsController: SettingsController by inject()
    private val proxies = FXCollections.observableArrayList<String>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var viperGirlsSettings: ViperSettings
    val viperSettingsModel = ViperSettingsModel()

    override val root = vbox {}

    init {
        coroutineScope.launch {
            viperGirlsSettings = settingsController.findViperGirlsSettings()
            viperSettingsModel.username = viperGirlsSettings.username
            viperSettingsModel.password = viperGirlsSettings.password
            viperSettingsModel.thanks = viperGirlsSettings.thanks
            viperSettingsModel.host = viperGirlsSettings.host
            viperSettingsModel.requestLimit = viperGirlsSettings.requestLimit
            viperSettingsModel.fetchMetadata = viperGirlsSettings.fetchMetadata
            proxies.addAll(settingsController.getProxies())
            runLater {
                with(root) {
                    form {
                        fieldset {
                            field("Viper domain") {
                                combobox(viperSettingsModel.hostProperty, proxies)
                            }
                            field("Rate limit (requests/s)") {
                                add(Spinner<Int>(1, 5, viperGirlsSettings.requestLimit.toInt()).apply {
                                    viperSettingsModel.requestLimitProperty.bind(valueProperty())
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
                                    isSelected = viperGirlsSettings.login
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
    }

    override fun onUndock() {
        coroutineScope.cancel()
    }
}