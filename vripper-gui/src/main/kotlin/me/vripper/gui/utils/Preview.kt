package me.vripper.gui.utils

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.stage.Popup
import javafx.stage.Stage
import kotlinx.coroutines.*
import me.vripper.gui.model.PostModel
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

    private suspend fun previewLoading(postEntityId: Long, url: String): ImageView? {
        return try {
            ByteArrayInputStream(ImageCacheManager.load(postEntityId, url)).use {
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

/**
 * Shows a hover preview when the pointer is over [this] node only. The caller
 * decides which part of a row or tile counts as the preview area.
 */
fun Node.showPreviewOnHover(
    preview: Preview,
    enabledProvider: () -> Boolean = { true },
    sourceProvider: () -> Pair<Long, List<String>>?,
) {
    setOnMouseEntered { event ->
        preview.cleanup()
        if (!enabledProvider()) return@setOnMouseEntered
        val (postEntityId, images) = sourceProvider() ?: return@setOnMouseEntered
        val previews = images.filter { it.isNotBlank() }.take(4)
        if (previews.isEmpty()) return@setOnMouseEntered
        preview.previewPopup.apply {
            x = event.screenX + 20
            y = event.screenY + 10
        }
        preview.display(postEntityId, previews)
    }
    setOnMouseMoved { event ->
        if (!enabledProvider()) return@setOnMouseMoved
        preview.previewPopup.apply {
            x = event.screenX + 20
            y = event.screenY + 10
        }
    }
    setOnMouseExited { preview.cleanup() }
}

/**
 * Convenience overload for post tiles and rows.
 */
fun Node.showPostPreviewOnHover(
    preview: Preview,
    enabledProvider: () -> Boolean = { true },
    postProvider: () -> PostModel?,
) {
    showPreviewOnHover(preview, enabledProvider) {
        postProvider()?.let { post -> post.id to post.previewList }
    }
}
