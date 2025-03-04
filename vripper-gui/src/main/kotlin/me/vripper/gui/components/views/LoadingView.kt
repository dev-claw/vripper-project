package me.vripper.gui.components.views

import javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS
import javafx.scene.effect.DropShadow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.gui.VripperGuiApplication
import me.vripper.gui.components.fragments.SessionFragment
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.listener.GuiStartupLister
import me.vripper.gui.services.GrpcEndpointService
import me.vripper.gui.utils.Watcher
import me.vripper.utilities.DatabaseManager
import tornadofx.*

class LoadingView : View("VRipper") {

    private val widgetsController: WidgetsController by inject()
    private val grpcEndpointService: GrpcEndpointService by di("remoteAppEndpointService")
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val root = borderpane {}

    init {
        coroutineScope.launch {
            GuiEventBus.events.filterIsInstance(GuiEventBus.ApplicationInitialized::class).collect {
                if (widgetsController.currentSettings.localSession) {
                    DatabaseManager.connect()
                    GuiStartupLister().run()
                    runLater {
                        replaceWith(find<AppView>())
                    }
                } else {
                    grpcEndpointService.connect(
                        widgetsController.currentSettings.remoteSessionModel.host,
                        widgetsController.currentSettings.remoteSessionModel.port
                    )
                    repeat(10) {
                        if (grpcEndpointService.ready()) {
                            return@repeat
                        }
                        delay(333)
                    }
                    runLater {
                        replaceWith(find<AppView>())
                        runBlocking {
                            GuiEventBus.publishEvent(GuiEventBus.RemoteSessionFailure)
                        }
                    }
                    if (!grpcEndpointService.ready()) {
                        val sessionView = find<SessionFragment>()
                        runLater {
                            sessionView.openModal()?.apply {
                                setOnCloseRequest {
                                    VripperGuiApplication.APP_INSTANCE.stop()
                                }
                                minWidth = 100.0
                                minHeight = 100.0
                            }
                        }
                    }
                }

                if (it.args.isNotEmpty()) {
                    Watcher.notify(it.args[0])
                }
            }
        }

        with(root) {
            padding = insets(all = 5)
            center {
                progressindicator {
                    progress = INDETERMINATE_PROGRESS
                }
            }
            effect = DropShadow()
        }
    }

    override fun onUndock() {
        coroutineScope.cancel()
    }
}