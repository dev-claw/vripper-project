package me.vripper.gui.components.fragments

import atlantafx.base.util.IntegerStringConverter
import javafx.scene.control.Spinner
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.settings.ImageViewerSettingsModel
import tornadofx.*

class ImageViewerSettingsFragment : Fragment("Image Viewer") {

    private val widgetsController: WidgetsController by inject()

    /**
     * Staged copy of the viewer settings: edited here and only committed to the
     * persisted widget settings when the Settings dialog is saved, matching the
     * server-side tabs (which also require an explicit save).
     */
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
                fieldset("Display") {
                    field("Transitions") {
                        add(
                            checkbox(
                                "Fade animation when switching images",
                                imageViewerSettingsModel.fadeAnimationProperty
                            )
                        )
                    }
                }
                fieldset("Memory") {
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
                }
                fieldset("Background preloading") {
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
