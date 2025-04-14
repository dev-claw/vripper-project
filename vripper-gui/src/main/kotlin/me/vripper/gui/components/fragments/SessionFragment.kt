package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import atlantafx.base.util.IntegerStringConverter
import javafx.geometry.Pos
import javafx.scene.control.RadioButton
import javafx.scene.control.Spinner
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import kotlinx.coroutines.runBlocking
import me.vripper.gui.VripperGuiApplication
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.services.GrpcEndpointService
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.listeners.AppManager
import tornadofx.*

class SessionFragment : Fragment("Change Session") {

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
                            runBlocking {
                                GuiEventBus.publishEvent(GuiEventBus.ChangingSession)
                                AppManager.stop()
                                grpcEndpointService.disconnect()
                            }
                            when ((selectedToggle as RadioButton).id) {
                                "localSession" -> {
                                    runBlocking {
                                        widgetsController.currentSettings.localSession = true
                                        AppEndpointManager.set(GuiEventBus.LocalSession)
                                        AppManager.start()
                                        GuiEventBus.publishEvent(GuiEventBus.LocalSession)
                                    }
                                }
                                "remoteSession" -> {
                                    runBlocking {
                                        widgetsController.currentSettings.localSession = false
                                        AppEndpointManager.set(GuiEventBus.RemoteSession)
                                        grpcEndpointService.connect(
                                            widgetsController.currentSettings.remoteSessionModel.host,
                                            widgetsController.currentSettings.remoteSessionModel.port,
                                            widgetsController.currentSettings.remoteSessionModel.passcode,
                                        )
                                        GuiEventBus.publishEvent(GuiEventBus.RemoteSession)
                                    }
                                }

                                else -> VripperGuiApplication.APP_INSTANCE.stop()
                            }
                            runLater {
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}