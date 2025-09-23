package me.vripper.gui.components.views

import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS
import javafx.scene.effect.DropShadow
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import me.vripper.gui.VripperGuiApplication
import me.vripper.gui.components.fragments.SessionFragment
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.services.GrpcEndpointService
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.gui.utils.ClipboardManager
import me.vripper.gui.utils.Watcher
import me.vripper.listeners.AppManager
import me.vripper.utilities.DatabaseManager
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class LoadingView : View("VRipper") {

    private val widgetsController: WidgetsController by inject()
    private val grpcEndpointService: GrpcEndpointService by di("remoteAppEndpointService")
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val message = SimpleStringProperty("")

    override val root = borderpane {}

    init {
        DatabaseManager.connect()
        ClipboardManager.init()
        coroutineScope.launch {
            GuiEventBus.events.filterIsInstance(GuiEventBus.ApplicationInitialized::class).collect {
                message.set("")
                AppManager.stop()
                grpcEndpointService.disconnect()
                if (widgetsController.currentSettings.localSession) {
                    AppEndpointManager.set(GuiEventBus.LocalSession)
                    GuiEventBus.publishEvent(GuiEventBus.LocalSession)
                    AppManager.start()
                } else {
                    AppEndpointManager.set(GuiEventBus.RemoteSession)
                    GuiEventBus.publishEvent(GuiEventBus.RemoteSession)
                    grpcEndpointService.connect(
                        widgetsController.currentSettings.remoteSessionModel.host,
                        widgetsController.currentSettings.remoteSessionModel.port,
                        widgetsController.currentSettings.remoteSessionModel.passcode,
                    )

                    val check = grpcEndpointService.versionCheck()
                    if (check == null) {
                        message.set("Unable to connect to ${widgetsController.currentSettings.remoteSessionModel.host}:${widgetsController.currentSettings.remoteSessionModel.port}")
                        return@collect
                    } else if (!check) {
                        println("Version Mismatch")
                        message.set("Version Mismatch, client must be >= 6.6.0")
                        return@collect
                    }
                }
                runLater {
                    replaceWith(find<AppView>())
                }
                if (it.args.isNotEmpty()) {
                    Watcher.notify(it.args[0])
                }
            }
        }

        with(root) {
            padding = insets(all = 5)
            top {
                menubar {
                    menu("File") {
                        item("Change session", KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN)) {
                            graphic = FontIcon.of(Feather.LINK_2)
                            action {
                                find<SessionFragment>().openModal()?.apply {
                                    minWidth = 100.0
                                    minHeight = 100.0
                                }
                            }
                        }
                        separator()
                        item("Exit", KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN)).apply {
                            graphic = FontIcon.of(Feather.X_SQUARE)
                            action {
                                VripperGuiApplication.APP_INSTANCE.stop()
                            }
                        }
                    }
                }.visibleWhen { message.isNotEmpty }
            }
            center {
                progressindicator {
                    progress = INDETERMINATE_PROGRESS
                }.visibleWhen { message.isEmpty }
                text(message).visibleWhen { message.isNotEmpty }
            }
            effect = DropShadow()
        }
    }

//    override fun onUndock() {
//        println("Undowking")
//        coroutineScope.cancel()
//    }
}