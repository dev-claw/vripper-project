package me.vripper.gui.components.fragments

import atlantafx.base.util.IntegerStringConverter
import javafx.scene.control.Spinner
import javafx.util.StringConverter
import me.vripper.gui.components.gridTileSizeSelector
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.settings.ImageViewerSettingsModel
import me.vripper.gui.model.settings.WorkspaceSettingsModel
import tornadofx.*

class ViewersSettingsFragment : Fragment("Viewers") {

    private val widgetsController: WidgetsController by inject()

    val overviewWindowSettingsModel = WorkspaceSettingsModel().apply {
        val current = widgetsController.currentSettings.workspaceSettingsModel
        browseLayout = current.browseLayout
        detailsPanelVisible = current.detailsPanelVisible
        detailsPanelDividerPosition = current.detailsPanelDividerPosition
        overviewGridTileSize = current.overviewGridTileSize.coerceIn(
            PostWorkspacePane.MIN_THUMB_SIZE,
            PostWorkspacePane.MAX_THUMB_SIZE,
        )
        windowWidth = current.windowWidth
        windowHeight = current.windowHeight
        windowX = current.windowX
        windowY = current.windowY
        windowMaximized = current.windowMaximized
    }

    val imageViewerSettingsModel = ImageViewerSettingsModel().apply {
        val current = widgetsController.currentSettings.imageViewerSettingsModel
        decodedCacheMb = current.decodedCacheMb
        bytePreloadRadius = current.bytePreloadRadius
        bytePreloadConcurrency = current.bytePreloadConcurrency
        decodePreloadConcurrency = current.decodePreloadConcurrency
        diskCacheMb = current.diskCacheMb
        fadeAnimation = current.fadeAnimation
    }

    override val root = vbox {}

    init {
        with(root) {
            form {
                fieldset("Overview Window") {
                    field("Default layout") {
                        combobox(overviewWindowSettingsModel.browseLayoutProperty, listOf("GRID", "TABLE")) {
                            converter = object : StringConverter<String>() {
                                override fun toString(mode: String?): String = when (mode) {
                                    "GRID" -> "Grid"
                                    else -> "Table"
                                }

                                override fun fromString(label: String?): String? = when (label) {
                                    "Grid" -> "GRID"
                                    "Table" -> "TABLE"
                                    else -> null
                                }
                            }
                        }
                    }
                    field("Tile size") {
                        add(
                            gridTileSizeSelector(
                                overviewWindowSettingsModel.overviewGridTileSizeProperty,
                                PostWorkspacePane.MIN_THUMB_SIZE,
                                PostWorkspacePane.MAX_THUMB_SIZE,
                            )
                        )
                    }
                    field("Details panel") {
                        checkbox(
                            "Show the details panel by default",
                            overviewWindowSettingsModel.detailsPanelVisibleProperty
                        )
                    }
                    field("Remembered position & size") {
                        button("Reset") {
                            overviewWindowSettingsModel.windowWidth = 900.0
                            overviewWindowSettingsModel.windowHeight = 650.0
                            overviewWindowSettingsModel.windowX = Double.NaN
                            overviewWindowSettingsModel.windowY = Double.NaN
                            overviewWindowSettingsModel.windowMaximized = false
                            overviewWindowSettingsModel.detailsPanelDividerPosition = 0.7
                        }
                    }
                }
                fieldset("Image Viewer") {
                    field("Transitions") {
                        add(
                            checkbox(
                                "Fade animation when switching images",
                                imageViewerSettingsModel.fadeAnimationProperty
                            )
                        )
                    }
                    field("Decoded frames cache (MB)") {
                        add(
                            Spinner<Int>(64, 4096, imageViewerSettingsModel.decodedCacheMb).apply {
                                valueProperty().onChange { imageViewerSettingsModel.decodedCacheMb = it!! }
                                isEditable = true
                                IntegerStringConverter.createFor(this)
                            })
                    }
                    field("Disk cache (MB)") {
                        add(
                            Spinner<Int>(16, 4096, imageViewerSettingsModel.diskCacheMb).apply {
                                valueProperty().onChange { imageViewerSettingsModel.diskCacheMb = it!! }
                                isEditable = true
                                IntegerStringConverter.createFor(this)
                            })
                    }
                    field("Byte preload radius (images)") {
                        add(
                            Spinner<Int>(0, 20, imageViewerSettingsModel.bytePreloadRadius).apply {
                                valueProperty().onChange { imageViewerSettingsModel.bytePreloadRadius = it!! }
                                isEditable = true
                                IntegerStringConverter.createFor(this)
                            })
                    }
                    field("Byte preload concurrency") {
                        add(
                            Spinner<Int>(1, 8, imageViewerSettingsModel.bytePreloadConcurrency).apply {
                                valueProperty().onChange { imageViewerSettingsModel.bytePreloadConcurrency = it!! }
                                isEditable = true
                                IntegerStringConverter.createFor(this)
                            })
                    }
                    field("Decode preload concurrency") {
                        add(
                            Spinner<Int>(1, 4, imageViewerSettingsModel.decodePreloadConcurrency).apply {
                                valueProperty().onChange { imageViewerSettingsModel.decodePreloadConcurrency = it!! }
                                isEditable = true
                                IntegerStringConverter.createFor(this)
                            })
                    }
                }
            }
        }
    }
}
