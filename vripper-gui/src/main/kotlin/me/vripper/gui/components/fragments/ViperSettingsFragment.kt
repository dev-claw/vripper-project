package me.vripper.gui.components.fragments

import atlantafx.base.controls.ToggleSwitch
import javafx.collections.FXCollections
import javafx.scene.control.Spinner
import kotlinx.coroutines.runBlocking
import me.vripper.gui.controller.SettingsController
import me.vripper.gui.model.settings.ViperSettingsModel
import me.vripper.model.ViperSettings
import me.vripper.gui.fx.*

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
        viperSettingsModel.watchSubscriptions = viperSettings.watchSubscriptions
        viperSettingsModel.watchIntervalMinutes = viperSettings.watchIntervalMinutes
        viperSettingsModel.watchThreads = viperSettings.watchThreads
        proxies.addAll(runBlocking { settingsController.getProxies() })
        with(root) {
            form {
                fieldset("Connection") {
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
                }
                fieldset("Account") {
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
                fieldset("Watcher") {
                    field("Watch Thread Library") {
                        checkbox {
                            bind(viperSettingsModel.watchThreadsProperty)
                        }
                    }
                    field("Watch subscriptions") {
                        checkbox {
                            bind(viperSettingsModel.watchSubscriptionsProperty)
                        }
                    }
                    field("Watcher interval (minutes)") {
                        add(Spinner<Int>(5, 1440, viperSettingsModel.watchIntervalMinutes).apply {
                            valueProperty().onChange {
                                viperSettingsModel.watchIntervalMinutes = it!!
                            }
                            isEditable = true
                            atlantafx.base.util.IntegerStringConverter.createFor(this)
                        })
                    }
                }
            }
        }
    }
}