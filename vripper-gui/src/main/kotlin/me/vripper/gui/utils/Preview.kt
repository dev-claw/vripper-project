package me.vripper.gui.utils

import javafx.geometry.Pos
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.stage.Popup
import javafx.stage.Stage
import kotlinx.coroutines.*
import tornadofx.clear
import tornadofx.runLater
import tornadofx.sortWith
import java.io.ByteArrayInputStream

class Preview(val owner: Stage) {

    private var hBox: HBox = HBox().apply { spacing = 5.0; alignment = Pos.BOTTOM_CENTER }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val previewPopup = Popup().apply {
        content.add(hBox)
    }

    val jobs = mutableListOf<Job>()


    fun display(postEntityId: Long, images: List<String>) {
        cleanup()
        previewPopup.show(owner)
        val indexedResults = mutableMapOf<ImageView, Int>()
        images.forEachIndexed { index, url ->
            coroutineScope.launch {
                val imageView = previewLoading(postEntityId, url)
                if (imageView != null) {
                    if (isActive) {
                        runLater {
                            indexedResults[imageView] = index
                            hBox.children.add(imageView)
                            hBox.children.sortWith { o1, o2 ->
                                val index1 = indexedResults[o1] ?: 0
                                val index2 = indexedResults[o2] ?: 0
                                index1.compareTo(index2)
                            }
                        }
                    }
                }
            }.also { synchronized(this) { jobs.add(it) } }
        }
    }

    fun cleanup() {
        synchronized(this) {
            jobs.forEach { it.cancel() }
            jobs.clear()
        }
        previewPopup.hide()
        hBox.clear()
    }

    fun destroy() {
        cleanup()
        coroutineScope.cancel()
    }

    private fun previewLoading(postEntityId: Long, url: String): ImageView? {
        return try {
            ByteArrayInputStream(PreviewCacheManager.load(postEntityId, url)).use {
                ImageView(Image(it)).apply {
                    isPreserveRatio = true

                    fitWidth = if (image.width > 200.0) {
                        200.0
                    } else {
                        image.width
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}