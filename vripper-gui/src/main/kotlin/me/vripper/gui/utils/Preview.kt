package me.vripper.gui.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Pos
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.stage.Popup
import javafx.stage.Stage
import kotlinx.coroutines.*
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.hash256
import tornadofx.bind
import tornadofx.runLater
import tornadofx.sortWith
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class Preview(owner: Stage, private val images: List<String>, private val cachePath: Path) {

    private var hBox: HBox = HBox().apply { spacing = 5.0; alignment = Pos.BOTTOM_CENTER }
    private val log by LoggerDelegate()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val previewPopup = Popup()

    private val cache: LoadingCache<String, ByteArray> = Caffeine
        .newBuilder()
        .weigher { _: String, value: ByteArray -> value.size }
        .maximumWeight(1024 * 1024 * 100)
        .build(::load)

    private fun load(url: String): ByteArray {
        val path = cachePath.resolve(url.hash256())
        if (Files.exists(path) && Files.isRegularFile(path)) {
            return Files.readAllBytes(path)
        }
        return URI.create(url).toURL().openStream().use { `is` ->
            val bytes = `is`.readAllBytes()
            Files.write(path, bytes)
            bytes
        }
    }

    init {
        Files.createDirectories(cachePath)
        val loaded: ObservableList<Pair<ImageView, Int>> = FXCollections.observableArrayList()
        hBox.children.bind(loaded) {
            it.first
        }
        previewPopup.content.add(hBox)
        previewPopup.show(owner)
        coroutineScope.launch {
            images.forEachIndexed { index, url ->
                coroutineScope.launch {
                    val imageView = previewLoading(url).await()
                    if (imageView != null) {
                        runLater {
                            loaded.add(Pair(imageView, index))
                            loaded.sortWith { o1, o2 -> o1.second.compareTo(o2.second) }
                        }
                    }
                }
            }
        }
    }

    fun hide() {
        coroutineScope.cancel()
        previewPopup.hide()
    }

    private fun previewLoading(url: String): Deferred<ImageView?> {
        return coroutineScope.async {
            try {
                ByteArrayInputStream(cache[url]).use {
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
}