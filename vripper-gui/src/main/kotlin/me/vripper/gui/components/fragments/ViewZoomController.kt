package me.vripper.gui.components.fragments

import javafx.scene.image.Image
import kotlin.math.abs
import kotlin.math.min

/**
 * Owns the view-mode zoom/fit/minimap presentation state: the current zoom, the
 * fit zoom computed from the window bounds, and the resize of the image view.
 * UI side effects (toolbar slider, title/status bar, deep-zoom request) are
 * supplied as callbacks so this class stays independent of the fragment.
 */
internal class ViewZoomController(
    private val canvas: ImageCanvasPane,
    private val pipeline: ImageLoadPipeline,
    private val currentIndex: () -> Int,
    private val missingPlaceholder: () -> Image,
    private val availableSize: () -> Pair<Double, Double>,
    private val outputScale: () -> Double,
    private val maxZoom: Double,
    private val onZoomControls: (zoom: Double, fitZoom: Double) -> Unit,
    private val onTitleRefresh: () -> Unit,
    private val onZoomApplied: (immediateDeepZoom: Boolean) -> Unit
) {

    var zoom = 1.0
        private set

    var fitZoom = 1.0
        private set

    /** Whether the zoom is currently snapped to the fit zoom. */
    fun isAtFit(): Boolean = abs(zoom - fitZoom) < 1e-6

    /**
     * Recomputes the fit zoom for the current viewport and image, then applies
     * the (possibly preserved) zoom. [resetToFit] snaps the zoom back to fit,
     * used when navigating to a different image.
     */
    fun recomputeFit(resetToFit: Boolean, immediateDeepZoom: Boolean = false) {
        val newFit = computeFitZoom()
        val wasAtFit = isAtFit()
        fitZoom = newFit
        val target = when {
            resetToFit -> newFit
            wasAtFit -> newFit
            zoom < newFit -> newFit
            else -> zoom
        }
        setZoom(target, immediateDeepZoom)
    }

    fun setZoom(newZoom: Double, immediateDeepZoom: Boolean = false) {
        val clamped = newZoom.coerceIn(fitZoom, maxZoom)
        zoom = clamped
        onZoomControls(clamped, fitZoom)
        applyZoom(immediateDeepZoom)
    }

    fun zoomIn(step: Double) = setZoom(zoom * step)

    fun zoomOut(step: Double) = setZoom(zoom / step)

    /**
     * The scale (fraction of the real resolution) needed to render the current
     * image crisply at the current zoom, never exceeding the source.
     *
     * Returns 0 when no real image is displayed (the missing placeholder):
     * its synthetic 1.0 fit zoom must not turn a later reload into a
     * full-resolution decode.
     */
    fun desiredDisplayScale(): Double {
        if (currentSourceSize() == null) return 0.0
        return min(1.0, zoom * outputScale())
    }

    /**
     * Real resolution of the currently displayed image, falling back to the
     * decoded image size when the header could not be parsed.
     */
    fun currentSourceSize(): Pair<Int, Int>? {
        // The 1x1 placeholder is not a real image: report no size so the title,
        // status bar and minimap do not show "1 × 1".
        if (canvas.imageView.image === missingPlaceholder()) return null
        val index = currentIndex()
        pipeline.sourceSize(index)?.let { return it }
        val img = canvas.imageView.image ?: return null
        val width = img.width.toInt()
        val height = img.height.toInt()
        return if (width > 0 && height > 0) width to height else null
    }

    fun refreshMinimapSize() {
        val size = currentSourceSize() ?: run {
            canvas.hideMinimap()
            return
        }
        canvas.refreshMinimapSize(size.first, size.second)
    }

    fun refreshMinimapViewport() = canvas.refreshMinimapViewport()

    fun hideMinimap() = canvas.hideMinimap()

    fun applyZoom(immediateDeepZoom: Boolean = false) {
        val size = currentSourceSize()
        if (size != null) {
            val (_, sourceHeight) = size
            val view = canvas.imageView
            view.isPreserveRatio = true
            view.fitWidth = 0.0
            view.fitHeight = (sourceHeight * zoom).coerceAtLeast(1.0)
        }
        onTitleRefresh()
        onZoomApplied(immediateDeepZoom)
    }

    /**
     * The fit zoom is the largest scale (capped at 100%) at which the image
     * fits inside the window. Computed from the window bounds rather than the
     * ScrollPane viewport so the appearing/disappearing scrollbars cannot make
     * it oscillate.
     */
    private fun computeFitZoom(): Double {
        val size = currentSourceSize() ?: return 1.0
        val (sourceWidth, sourceHeight) = size
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1.0
        val (availableWidth, availableHeight) = availableSize()
        if (availableWidth <= 1.0 || availableHeight <= 1.0) return 1.0
        return min(1.0, min(availableWidth / sourceWidth, availableHeight / sourceHeight))
    }
}
