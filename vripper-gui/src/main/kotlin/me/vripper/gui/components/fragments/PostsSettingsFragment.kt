package me.vripper.gui.components.fragments

import atlantafx.base.controls.ToggleSwitch
import javafx.util.StringConverter
import me.vripper.gui.components.gridTileSizeSelector
import me.vripper.gui.components.views.PostsGridView
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.settings.PostsSettingsModel
import tornadofx.*

class PostsSettingsFragment : Fragment("Posts") {

    private val widgetsController: WidgetsController by inject()

    val postsSettingsModel = PostsSettingsModel().apply {
        val current = widgetsController.currentSettings
        postsViewMode = current.postsViewMode
        postsGridTileSize = current.postsGridTileSize.coerceIn(
            PostsGridView.MIN_TILE_SIZE,
            PostsGridView.MAX_TILE_SIZE,
        )
        postsPreviewPopup = current.postsPreviewPopup
    }

    override val root = vbox {}

    init {
        with(root) {
            form {
                fieldset("Layout") {
                    field("Default layout") {
                        combobox(postsSettingsModel.postsViewModeProperty, listOf("TABLE", "LIST", "GRID")) {
                            converter = object : StringConverter<String>() {
                                override fun toString(mode: String?): String = when (mode) {
                                    "LIST" -> "List"
                                    "GRID" -> "Grid"
                                    else -> "Table"
                                }

                                override fun fromString(label: String?): String? = when (label) {
                                    "List" -> "LIST"
                                    "Grid" -> "GRID"
                                    "Table" -> "TABLE"
                                    else -> null
                                }
                            }
                        }
                    }
                }
                fieldset("Grid") {
                    field("Tile size") {
                        add(
                            gridTileSizeSelector(
                                postsSettingsModel.postsGridTileSizeProperty,
                                PostsGridView.MIN_TILE_SIZE,
                                PostsGridView.MAX_TILE_SIZE,
                            )
                        )
                    }
                }
                fieldset("Preview") {
                    field("Preview popups") {
                        add(buildPreviewPopupToggle())
                    }
                }
            }
        }
    }

    private fun buildPreviewPopupToggle(): ToggleSwitch = ToggleSwitch().apply {
        isSelected = postsSettingsModel.postsPreviewPopup
        selectedProperty().onChange { enabled ->
            postsSettingsModel.postsPreviewPopup = enabled
        }
    }
}
