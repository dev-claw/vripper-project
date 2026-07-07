package me.vripper.gui.components.fragments

import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.settings.WorkspaceSettingsModel
import tornadofx.*

class WorkspaceSettingsFragment : Fragment("Workspace") {

    private val widgetsController: WidgetsController by inject()

    /**
     * Staged copy of the workspace settings, committed only when the Settings
     * dialog is saved (like the image viewer tab).
     */
    val workspaceSettingsModel = WorkspaceSettingsModel().apply {
        val current = widgetsController.currentSettings.workspaceSettingsModel
        browseLayout = current.browseLayout
        detailsPanelVisible = current.detailsPanelVisible
        detailsPanelDividerPosition = current.detailsPanelDividerPosition
        windowWidth = current.windowWidth
        windowHeight = current.windowHeight
        windowX = current.windowX
        windowY = current.windowY
        windowMaximized = current.windowMaximized
    }

    override val root = vbox {}

    init {
        with(root) {
            form {
                fieldset("Overview") {
                    field("Default layout") {
                        combobox(workspaceSettingsModel.browseLayoutProperty, listOf("GRID", "TABLE"))
                    }
                    field("Details panel") {
                        checkbox(
                            "Show the details panel by default",
                            workspaceSettingsModel.detailsPanelVisibleProperty
                        )
                    }
                }
                fieldset("Window") {
                    field("Remembered position & size") {
                        button("Reset") {
                            workspaceSettingsModel.windowWidth = 900.0
                            workspaceSettingsModel.windowHeight = 650.0
                            workspaceSettingsModel.windowX = Double.NaN
                            workspaceSettingsModel.windowY = Double.NaN
                            workspaceSettingsModel.windowMaximized = false
                            workspaceSettingsModel.detailsPanelDividerPosition = 0.7
                        }
                    }
                }
            }
        }
    }
}
