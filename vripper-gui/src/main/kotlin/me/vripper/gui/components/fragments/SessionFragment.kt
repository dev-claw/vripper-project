package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import atlantafx.base.util.IntegerStringConverter
import javafx.geometry.Pos
import javafx.scene.control.RadioButton
import javafx.scene.control.Spinner
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import me.vripper.gui.VripperGuiApplication
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.services.GrpcEndpointService
import me.vripper.gui.utils.ActiveUICoroutines
import me.vripper.listeners.AppManager
import tornadofx.*

class SessionFragment : Fragment("Change Session") {

    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val widgetsController: WidgetsController by inject()
    private val grpcEndpointService: GrpcEndpointService by di("remoteAppEndpointService")
    private val toggleGroup = ToggleGroup()
    override val root = VBox().apply {
        alignment = Pos.CENTER
        padding = insets(all = 5)
        spacing = 5.0
    }

    init {
        with(root) {
            form {
                fieldset("Source") {
                    radiobutton(text = "Start Local Session", group = toggleGroup) {
                        id = "localSession"
                        isSelected = widgetsController.currentSettings.localSession
                    }
                    val remoteRadio = radiobutton(text = "Connect to Remote Session", group = toggleGroup) {
                        id = "remoteSession"
                        isSelected = !widgetsController.currentSettings.localSession
                    }
                    field("Host") {
                        enableWhen { remoteRadio.selectedProperty() }
                        textfield(widgetsController.currentSettings.remoteSessionModel.hostProperty) {}
                    }
                    field("Port") {
                        enableWhen { remoteRadio.selectedProperty() }
                        add(Spinner<Int>(0, 65535, widgetsController.currentSettings.remoteSessionModel.port).apply {
                            widgetsController.currentSettings.remoteSessionModel.portProperty.bind(valueProperty())
                            isEditable = true
                            IntegerStringConverter.createFor(this)
                        })
                    }
                    field("Passcode") {
                        enableWhen { remoteRadio.selectedProperty() }
                        passwordfield(widgetsController.currentSettings.remoteSessionModel.passcodeProperty) {}
                    }
                }
            }
            borderpane {
                right {
                    padding = insets(all = 5.0)
                    button("Ok") {
                        enableWhen { toggleGroup.selectedToggleProperty().isNotNull }
                        addClass(Styles.ACCENT)
                        isDefaultButton = true
                        action {
                            val selectedToggle = toggleGroup.selectedToggle
                            if (selectedToggle == null) {
                                VripperGuiApplication.APP_INSTANCE.stop()
                            }
                            when ((selectedToggle as RadioButton).id) {
                                "localSession" -> {
                                    coroutineScope.launch {
                                        AppManager.stop()
                                        widgetsController.currentSettings.localSession = true
                                        GuiEventBus.publishEvent(GuiEventBus.ChangingSession)
                                        while (ActiveUICoroutines.all().isNotEmpty()) {
                                            delay(200)
                                        }
                                        grpcEndpointService.disconnect()
                                        AppManager.start()
                                        GuiEventBus.publishEvent(GuiEventBus.LocalSession)
                                        runLater {
                                            close()
                                        }
                                    }
                                }

                                "remoteSession" -> {
                                    coroutineScope.launch {
                                        AppManager.stop()
                                        widgetsController.currentSettings.localSession = false
                                        GuiEventBus.publishEvent(GuiEventBus.ChangingSession)
                                        while (ActiveUICoroutines.all().isNotEmpty()) {
                                            delay(200)
                                        }
                                        grpcEndpointService.connect(
                                            widgetsController.currentSettings.remoteSessionModel.host,
                                            widgetsController.currentSettings.remoteSessionModel.port,
                                            widgetsController.currentSettings.remoteSessionModel.passcode,
                                        )
                                        GuiEventBus.publishEvent(GuiEventBus.RemoteSession)
                                        runLater {
                                            close()
                                        }
                                    }
                                }

                                else -> VripperGuiApplication.APP_INSTANCE.stop()
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