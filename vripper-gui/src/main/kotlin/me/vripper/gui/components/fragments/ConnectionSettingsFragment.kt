package me.vripper.gui.components.fragments

import javafx.scene.control.Spinner
import kotlinx.coroutines.*
import me.vripper.gui.controller.SettingsController
import me.vripper.gui.model.settings.ConnectionSettingsModel
import me.vripper.model.ConnectionSettings
import tornadofx.*

class ConnectionSettingsFragment : Fragment("Connection Settings") {
    private val settingsController: SettingsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connectionSettings: ConnectionSettings
    val connectionSettingsModel = ConnectionSettingsModel()

    override val root = vbox {}

    init {
        coroutineScope.launch {
            async { connectionSettings = settingsController.findConnectionSettings() }.await()
            runLater {
                with(root) {
                    form {
                        fieldset {
                            field("Concurrent downloads per host") {
                                add(Spinner<Int>(1, 4, connectionSettings.maxConcurrentPerHost).apply {
                                    connectionSettingsModel.maxThreadsProperty.bind(valueProperty())
                                    isEditable = true
                                    atlantafx.base.util.IntegerStringConverter.createFor(this)
                                })
                            }
                            field("Global concurrent downloads") {
                                add(Spinner<Int>(0, 24, connectionSettings.maxGlobalConcurrent).apply {
                                    connectionSettingsModel.maxTotalThreadsProperty.bind(valueProperty())
                                    isEditable = true
                                    atlantafx.base.util.IntegerStringConverter.createFor(this)
                                })
                            }
                            field("Connection timeout (s)") {
                                add(Spinner<Int>(1, 300, connectionSettings.timeout).apply {
                                    connectionSettingsModel.timeoutProperty.bind(valueProperty())
                                    isEditable = true
                                    atlantafx.base.util.IntegerStringConverter.createFor(this)
                                })
                            }
                            field("Maximum attempts") {
                                add(Spinner<Int>(1, 10, connectionSettings.maxAttempts).apply {
                                    connectionSettingsModel.maxAttemptsProperty.bind(valueProperty())
                                    isEditable = true
                                    atlantafx.base.util.IntegerStringConverter.createFor(this)
                                })
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