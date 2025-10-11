package me.vripper.gui.components.views

import javafx.geometry.Orientation
import javafx.scene.control.SplitPane
import javafx.scene.layout.Priority
import kotlinx.coroutines.*
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.utils.WidgetSettings
import tornadofx.*

class PostsTabView : View() {
    override val root: SplitPane = splitpane()

    private val postsTableView: PostsTableView by inject()
    private val postInfoView: PostInfoView by inject()
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob())

    init {
        with(root) {
            add(postsTableView)
            if (widgetsController.currentSettings.visibleInfoPanel) {
                postInfoView.setPostId(null)
                add(postInfoView)
                setDividerPositions(widgetsController.currentSettings.infoPanelDividerPosition)
            }
            orientation = Orientation.VERTICAL
            vboxConstraints { vGrow = Priority.ALWAYS }
        }
        titleProperty.bind(postsTableView.items.sizeProperty.map {
            if (it.toLong() > 0) {
                "Download (${it.toLong()})"
            } else {
                "Download"
            }
        })
        postsTableView.tableView.selectionModel.selectedItemProperty().onChange {
            if (widgetsController.currentSettings.visibleInfoPanel) {
                postInfoView.setPostId(it?.id)
            }
        }
        widgetsController.currentSettings.visibleInfoPanelProperty.onChange { visible ->
            if (visible) {
                val selectedItem = postsTableView.tableView.selectedItem
                postInfoView.setPostId(selectedItem?.id)
                root.add(postInfoView)
                root.setDividerPositions(widgetsController.currentSettings.infoPanelDividerPosition)
            } else {
                postInfoView.setPostId(null)
                root.items.removeIf { it.id == "postinfo_panel" }
            }
        }
        coroutineScope.launch {
            var lastDividerPosition = widgetsController.currentSettings.infoPanelDividerPosition
            while (isActive) {
                if (widgetsController.currentSettings.visibleInfoPanel) {
                    val dividerPosition = root.dividerPositions.firstOrNull()
                    if (dividerPosition != null && lastDividerPosition != dividerPosition) {
                        lastDividerPosition = dividerPosition
                        widgetsController.currentSettings.infoPanelDividerPosition = lastDividerPosition
                        WidgetSettings.update(widgetsController.currentSettings)
                    }
                }
                delay(1_000)
            }
        }
        println("${this.javaClass.name} init")
    }
}