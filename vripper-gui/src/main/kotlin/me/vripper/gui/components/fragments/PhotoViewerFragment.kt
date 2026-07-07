package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleIntegerProperty
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import kotlinx.coroutines.*
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.math.min

class PhotoViewerFragment : Fragment("Image Viewer") {

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sources: List<ImageSource> by param()
    val initialIndex: Int by param()

    private val indexProperty = SimpleIntegerProperty(initialIndex)

    private val imageView = ImageView().apply {
        isPreserveRatio = true
        isSmooth = true
        isCache = true
    }

    private val cache = object : LinkedHashMap<Int, Image>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Image>?): Boolean = size > 3
    }

    private var bottomBar: HBox

    override val root = BorderPane().apply {
        center = imageView
        BorderPane.setAlignment(imageView, Pos.CENTER)

        val prevButton = Button().apply {
            graphic = FontIcon.of(Feather.ARROW_LEFT)
            addClass(Styles.ACCENT)
            setOnAction {
                if (sources.isEmpty()) return@setOnAction
                coroutineScope.launch {
                    goTo((indexProperty.get() - 1 + sources.size) % sources.size)
                }
            }
        }

        val nextButton = Button().apply {
            graphic = FontIcon.of(Feather.ARROW_RIGHT)
            addClass(Styles.ACCENT)
            setOnAction {
                if (sources.isEmpty()) return@setOnAction
                coroutineScope.launch {
                    goTo((indexProperty.get() + 1) % sources.size)
                }
            }
        }

        val label = Label().apply {
            textProperty().bind(indexProperty.plus(1).asString().concat(" / ${sources.size}"))
        }

        bottomBar = HBox(10.0, prevButton, label, nextButton).apply {
            alignment = Pos.CENTER
            paddingBottom = 5.0
        }
        bottom = bottomBar

        fun scheduleResize() {
            runLater { resizeNoUpscale() }
        }

        // Re-evaluate whenever container size or bottom bar size changes
        layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }
        bottomBar.layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }

        // Re-evaluate whenever the image changes (so it works on prev/next)
        imageView.imageProperty().addListener { _, _, _ -> scheduleResize() }
    }

    init {
        coroutineScope.launch {
            if (sources.isEmpty()) {
                return@launch
            }
            val image = loadImageFor(indexProperty.value)
            runLater {
                imageView.image = image
                title = sources[indexProperty.value].fileName()
                resizeNoUpscale()
            }
        }

    }

    private suspend fun goTo(newIndex: Int) {
        if (sources.isEmpty()) return

        val image = loadImageFor(newIndex)
        runLater {
            imageView.image = image
            indexProperty.set(newIndex)
            title = sources[newIndex].fileName()
        }

        // preload neighbors only
        val prev = (newIndex - 1 + sources.size) % sources.size
        val next = (newIndex + 1) % sources.size
        preload(prev)
        preload(next)
    }

    private fun resizeNoUpscale() {
        val img = imageView.image ?: return
        val ih = img.height
        if (ih <= 0.0) return

        val bottomH = bottomBar.layoutBounds.height
        val availableH = (root.height - bottomH - 10.0).coerceAtLeast(0.0)

        val scale = min(1.0, availableH / ih) // never upscale
        imageView.isPreserveRatio = true
        imageView.fitHeight = ih * scale
        imageView.fitWidth = 0.0
    }

    private suspend fun preload(i: Int) {
        if (i == indexProperty.get()) return
        if (cache.containsKey(i)) return
        cache[i] = loadImageFor(i)
    }

    private suspend fun loadImageFor(i: Int): Image {
        cache[i]?.let { return it }
        val source = sources[i]
        val inputStream = source.inputStream()
        val img = if (inputStream == null) {
            missingPlaceholder
        } else {
            Image(inputStream, 0.0, 0.0, true, true)
        }
        cache[i] = img
        return img
    }

    private val missingPlaceholder: Image by lazy {
        // 1x1 transparent PNG
        val transparentPngBase64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/xcAAgMBgX7Y1z8AAAAASUVORK5CYII="
        val bytes = Base64.getDecoder().decode(transparentPngBase64)
        Image(ByteArrayInputStream(bytes), 0.0, 0.0, true, true)
    }

    override fun onUndock() {
        coroutineScope.cancel()
    }
}