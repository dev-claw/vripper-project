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
import me.vripper.gui.components.views.AppView
import me.vripper.gui.components.views.LoadingView
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import tornadofx.*

class SessionFragment : Fragment("Change Session") {

    private val widgetsController: WidgetsController by inject()
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
                        this.id = "localSession"
                        isSelected = widgetsController.currentSettings.localSession
                    }
                    val remoteRadio = radiobutton(text = "Connect to Remote Session", group = toggleGroup) {
                        this.id = "remoteSession"
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
                            runBlocking {
                                GuiEventBus.publishEvent(GuiEventBus.ChangingSession)
                            }
                            val selectedToggle = toggleGroup.selectedToggle
                            runLater {
                                find<AppView>().replaceWith(find<LoadingView>())
                            }
                            runLater {
                                when ((selectedToggle as RadioButton).id) {
                                    "localSession" -> {
                                        widgetsController.currentSettings.localSession = true
                                    }

                                    "remoteSession" -> {
                                        widgetsController.currentSettings.localSession = false
                                    }

                                    else -> VripperGuiApplication.APP_INSTANCE.stop()
                                }
                                runBlocking {
                                    GuiEventBus.publishEvent(GuiEventBus.ApplicationInitialized(emptyList()))
                                }
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}