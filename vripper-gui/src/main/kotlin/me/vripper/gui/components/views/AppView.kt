package me.vripper.gui.components.views

import atlantafx.base.theme.*
import javafx.application.Application.setUserAgentStylesheet
import javafx.event.EventHandler
import javafx.scene.input.TransferMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.gui.controller.AppController
import me.vripper.gui.controller.MainController
import me.vripper.gui.controller.WidgetsController
import tornadofx.View
import tornadofx.onChange
import tornadofx.vbox
import java.net.URI

class AppView : View() {

    override val root = vbox { }
    private val mainController: MainController by inject()
    private val menuBarView: MenuBarView by inject()
    private val mainView: MainView by inject()
    private val statusBarView: StatusBarView by inject()
    private val actionBarView: ActionBarView by inject()
    private val widgetsController: WidgetsController by inject()
    private val appController: AppController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        title = "VRipper ${mainController.version}"
        with(root) {
            add(menuBarView)
            if (widgetsController.currentSettings.visibleToolbarPanel) {
                add(actionBarView)
            }
            add(mainView)
            if (widgetsController.currentSettings.visibleStatusBarPanel) {
                add(statusBarView)
            }
            prefWidth = widgetsController.currentSettings.width
            prefHeight = widgetsController.currentSettings.height
            onDragOver = EventHandler { event ->
                if (event.dragboard.hasString()) {
                    event.acceptTransferModes(TransferMode.COPY)
                }
                event.consume()
            }
            onDragDropped = EventHandler { event ->
                val dragBoard = event.dragboard
                if (dragBoard != null && dragBoard.hasString()) {
                    val url = dragBoard.string.trim()
                    runCatching {
                        val uri = URI(url)
                        coroutineScope.launch {
                            appController.scan(uri.toString())
                        }
                    }
                }
            }
        }

        widgetsController.currentSettings.visibleToolbarPanelProperty.onChange { it ->
            if (it) {
                root.children.add(1, actionBarView.root)
            } else {
                root.children.removeIf { it.id == "action_toolbar" }
            }
        }

        widgetsController.currentSettings.visibleStatusBarPanelProperty.onChange { it ->
            if (it) {
                root.children.add(statusBarView.root)
            } else {
                root.children.removeIf { it.id == "statusbar" }
            }
        }

        widgetsController.currentSettings.themeProperty.onChange {
            when (it) {
                "CupertinoLight" -> setUserAgentStylesheet(CupertinoLight().userAgentStylesheet)
                "CupertinoDark" -> setUserAgentStylesheet(CupertinoDark().userAgentStylesheet)
                "NordLight" -> setUserAgentStylesheet(NordLight().userAgentStylesheet)
                "NordDark" -> setUserAgentStylesheet(NordDark().userAgentStylesheet)
                "PrimerLight" -> setUserAgentStylesheet(PrimerLight().userAgentStylesheet)
                "PrimerDark" -> setUserAgentStylesheet(PrimerDark().userAgentStylesheet)
                "Dracula" -> setUserAgentStylesheet(Dracula().userAgentStylesheet)
                else -> setUserAgentStylesheet(CupertinoLight().userAgentStylesheet)
            }
        }
    }
}