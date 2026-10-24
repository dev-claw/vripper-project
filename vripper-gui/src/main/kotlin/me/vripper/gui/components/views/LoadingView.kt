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
import me.vripper.gui.services.IGrpcEndpointService
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.gui.utils.ClipboardManager
import me.vripper.gui.utils.GuiErrorReporter
import me.vripper.gui.utils.Watcher
import me.vripper.listeners.AppManager
import me.vripper.utilities.DatabaseManager
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class LoadingView : View("VRipper") {

    private val widgetsController: WidgetsController by inject()
    private val grpcEndpointService: IGrpcEndpointService by di("remoteAppEndpointService")
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val message = SimpleStringProperty("")

    override val root = borderpane {}

    init {
        DatabaseManager.connect()
        coroutineScope.launch {
            GuiEventBus.events.filterIsInstance(GuiEventBus.ApplicationInitialized::class).collect {
                AppEndpointManager.set(GuiEventBus.ChangingSession)
                runLater {
                    runCatching { ClipboardManager.init() }
                        .onFailure { GuiErrorReporter.report("clipboard.startup", it) }
                    message.set("")
                }

                val localSession = widgetsController.currentSettings.localSession

                val stopped = GuiErrorReporter.call("startup.stop") { AppManager.stop() }
                if (stopped.isFailure) {
                    val detail = stopped.exceptionOrNull()
                        ?.let { "Unable to stop the previous session: ${GuiErrorReporter.message(it)}" }
                        ?: "Unable to stop the previous session"
                    runLater { message.set(detail) }
                    return@collect
                }
                GuiErrorReporter.call("startup.disconnect") { grpcEndpointService.disconnect() }

                if (localSession) {
                    val started = GuiErrorReporter.call("startup.start") { AppManager.start() }
                    if (started.isFailure) {
                        val detail = started.exceptionOrNull()
                            ?.let { "Unable to start the local session: ${GuiErrorReporter.message(it)}" }
                            ?: "Unable to start the local session"
                        runLater { message.set(detail) }
                        return@collect
                    }
                } else {
                    val remoteSettings = widgetsController.currentSettings.remoteSessionModel
                    val connection = GuiErrorReporter.call("startup.connect") {
                        grpcEndpointService.connect(
                            remoteSettings.host,
                            remoteSettings.port,
                            remoteSettings.passcode,
                        )
                    }
                    if (connection.isFailure) {
                        val detail = connection.exceptionOrNull()
                            ?.let { ": ${GuiErrorReporter.message(it)}" }
                            .orEmpty()
                        runLater {
                            message.set(
                                "Unable to connect to ${remoteSettings.host}:${remoteSettings.port}$detail"
                            )
                        }
                        return@collect
                    }

                    val version = GuiErrorReporter.call("startup.version") {
                        grpcEndpointService.versionCheck()
                    }
                    val check = version.getOrNull()
                    if (check == null) {
                        val detail = version.exceptionOrNull()
                            ?.let { ": ${GuiErrorReporter.message(it)}" }
                            .orEmpty()
                        GuiErrorReporter.call("startup.disconnect") { grpcEndpointService.disconnect() }
                        runLater {
                            message.set(
                                "Unable to connect to ${remoteSettings.host}:${remoteSettings.port}$detail"
                            )
                        }
                        return@collect
                    } else if (!check) {
                        GuiErrorReporter.call("startup.disconnect") { grpcEndpointService.disconnect() }
                        runLater {
                            message.set("Version Mismatch, client must be >= 6.6.0")
                        }
                        return@collect
                    }
                }

                val sessionType = if (localSession) GuiEventBus.LocalSession else GuiEventBus.RemoteSession
                AppEndpointManager.set(sessionType)

                runLater {
                    replaceWith(find<AppView>())
                    AppEndpointManager.coroutineScope.launch {
                        GuiEventBus.publishEvent(sessionType)
                    }
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
}