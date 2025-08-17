package me.vripper.gui.components.views

import atlantafx.base.theme.NordDark
import atlantafx.base.theme.NordLight
import javafx.application.Application
import kotlinx.coroutines.runBlocking
import me.vripper.gui.controller.MainController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tornadofx.View
import tornadofx.onChange
import tornadofx.runLater
import tornadofx.vbox

class AppView : View(), KoinComponent {

    override val root = vbox { }
    private val mainController: MainController by inject()
    private val menuBarView: MenuBarView by inject()
    private val mainView: MainView by inject()
    private val statusBarView: StatusBarView by inject()
    private val actionBarView: ActionBarView by inject()
    private val widgetsController: WidgetsController by inject<WidgetsController>()
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

        widgetsController.currentSettings.darkModeProperty.onChange {
            if (it) {
                Application.setUserAgentStylesheet(NordDark().userAgentStylesheet)
            } else {
                Application.setUserAgentStylesheet(NordLight().userAgentStylesheet)
            }
        }

        runLater {
            if (widgetsController.currentSettings.localSession) {
                runBlocking {
                    GuiEventBus.publishEvent(GuiEventBus.LocalSession)
                }
            } else {
                runBlocking {
                    GuiEventBus.publishEvent(GuiEventBus.RemoteSession)
                }
            }
        }
    }
}