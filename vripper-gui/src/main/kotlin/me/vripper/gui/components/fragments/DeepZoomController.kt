package me.vripper.gui.components.fragments

import javafx.animation.PauseTransition
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tornadofx.runLater

/**
 * Owns the deep-zoom state machine: when the user zooms past the resolution of
 * the cached window-sized decode, a higher-resolution copy of the current image
 * is decoded (debounced) and swapped in, then released when zooming back out.
 * Kept to the current index only so no more than one extra bitmap is held.
 */
internal class DeepZoomController(
    private val pipeline: ImageLoadPipeline,
    private val scope: CoroutineScope,
    private val imageView: ImageView,
    private val currentIndex: () -> Int,
    private val missingPlaceholder: () -> Image,
    private val desiredDisplayScale: () -> Double,
    private val windowReady: () -> Boolean,
    private val hasSources: () -> Boolean,
    private val onDeepImageApplied: () -> Unit
) {

    private var deepImage: Image? = null
    private var deepIndex = -1
    private var deepScale = 0.0
    private var deepDecodeJob: Job? = null
    private var pendingDeepScale = 0.0

    // Debounce so dragging the zoom slider does not trigger a decode per step.
    private val debounce = PauseTransition(Duration.millis(150.0)).apply {
        setOnFinished { performDecode() }
    }

    /** The image to display for [index]: the deep copy when it is current. */
    fun displayedImage(index: Int, base: Image): Image =
        if (deepIndex == index && deepImage != null) deepImage!! else base

    /**
     * Swaps in a higher-resolution decode of the current image while zoomed in
     * (and back to the cached window-sized one when zoomed out), so deep zoom
     * stays crisp without ever holding more than one extra bitmap.
     */
    fun update(immediate: Boolean = false) {
        val index = currentIndex()
        if (index < 0 || !hasSources()) return
        // Before the first layout the fit zoom is unknown (computeFitZoom falls
        // back to 100%), so requesting a deep decode here would decode the full
        // source resolution. The next layout pass recomputes the real fit zoom.
        if (!windowReady()) return
        val desired = desiredDisplayScale()
        if (desired <= 0.0) return

        val baseScale = pipeline.cachedScale(index)
        if (desired > baseScale * DEEP_ZOOM_RATIO) {
            // Deeper detail is needed than the cached image has: decode a
            // high-resolution copy unless we already have a good enough one.
            if (deepIndex == index && deepScale >= desired * 0.99) return
            request(desired, immediate)
        } else {
            // The cached image is enough: cancel any pending decode (so a stale
            // one cannot land after zooming back out) and release the
            // high-resolution copy.
            pendingDeepScale = 0.0
            debounce.stop()
            if (deepIndex == index) {
                deepDecodeJob?.cancel()
                deepImage = null
                deepIndex = -1
                deepScale = 0.0
                pipeline.cachedImage(index)?.let { imageView.image = it }
            }
        }
    }

    /** Drops any deep image/job, e.g. when navigating away or closing. */
    fun reset() {
        debounce.stop()
        deepDecodeJob?.cancel()
        deepDecodeJob = null
        deepImage = null
        deepIndex = -1
        deepScale = 0.0
        pendingDeepScale = 0.0
    }

    private fun request(scale: Double, immediate: Boolean) {
        pendingDeepScale = scale
        if (immediate) {
            // Navigation already knows the target zoom, so start the high-res
            // decode right away instead of waiting out the slider debounce.
            debounce.stop()
            performDecode()
        } else {
            debounce.playFromStart()
        }
    }

    private fun performDecode() {
        val index = currentIndex()
        val scale = pendingDeepScale
        if (index < 0 || scale <= 0.0) return
        deepDecodeJob?.cancel()
        deepDecodeJob = scope.launch {
            val image = pipeline.decodeAtScale(index, scale)
            if (!isActive) return@launch
            runLater {
                if (currentIndex() != index) return@runLater
                if (image === missingPlaceholder()) return@runLater
                if (pendingDeepScale < scale * 0.99) return@runLater
                if (deepScale >= scale) return@runLater
                deepImage = image
                deepIndex = index
                deepScale = scale
                imageView.image = image
                onDeepImageApplied()
            }
        }
    }

    companion object {
        // Re-decode for deep zoom only once the needed scale is this much larger
        // than the cached image, to avoid a decode per slider step.
        private const val DEEP_ZOOM_RATIO = 1.2
    }
}
