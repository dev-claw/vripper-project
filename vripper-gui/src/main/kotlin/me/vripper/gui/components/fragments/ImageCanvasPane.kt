package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import kotlin.math.max
import kotlin.math.min

/**
 * The image display surface: the scrollable, zoomable image view plus the
 * bottom-right overview minimap. Owns only presentation nodes and the minimap
 * interaction; the zoom/pan state and image loading remain in
 * [PostWorkspaceFragment].
 */
class ImageCanvasPane {

    val imageView = ImageView().apply {
        isPreserveRatio = true
        isSmooth = true
        // We manage decoded images ourselves; letting the node cache a full
        // resolution render doubles the native texture memory and can crash
        // the JVM on large photos.
        isCache = false
    }

    // Shown centred over the image area when the current image has no data
    // (not downloaded yet or unavailable), instead of a blank transparent image.
    val emptyLabel = Label("Image not available").apply {
        isVisible = false
        isMouseTransparent = true
        styleClass.add(Styles.TEXT_MUTED)
        style = "-fx-font-size: 16px;"
    }

    val contentPane = StackPane(imageView, emptyLabel).apply {
        alignment = Pos.CENTER
    }

    val scrollPane = ScrollPane(contentPane).apply {
        isPannable = true
        isFitToWidth = false
        isFitToHeight = false
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        // Let the StackPane stretch the scroll pane to fill the viewer.
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
    }

    // Bottom-right overview: a silhouette of the full image with a box
    // showing the portion of it currently visible in the viewport.
    private val minimapViewport = Rectangle().apply {
        fill = Color.rgb(255, 255, 255, 0.25)
        stroke = Color.WHITE
        strokeWidth = 1.0
        isManaged = false
    }

    private val minimap = Pane(minimapViewport).apply {
        isVisible = false
        isPickOnBounds = true
        cursor = Cursor.HAND
        style = "-fx-background-color: rgba(20,20,20,0.45);" +
                "-fx-background-radius: 4;" +
                "-fx-border-color: rgba(255,255,255,0.55);" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 4;"
        // Dragging anywhere in the silhouette pans the image; the box
        // follows because the scroll values drive the viewport position.
        setOnMousePressed { beginMinimapDrag(it) }
        setOnMouseDragged { dragMinimap(it) }
    }

    val centerStack = StackPane(scrollPane, minimap).apply {
        alignment = Pos.CENTER
    }

    // Current viewport rectangle within the minimap, as image fractions. Kept so
    // dragging the box can preserve the grab offset.
    private var minimapFractionX = 0.0
    private var minimapFractionY = 0.0
    private var minimapFractionWidth = 1.0
    private var minimapFractionHeight = 1.0
    private var minimapGrabOffsetX = 0.0
    private var minimapGrabOffsetY = 0.0

    // Logical size the minimap is laid out at, for the current image aspect.
    private var minimapDisplayWidth = 0.0
    private var minimapDisplayHeight = 0.0

    init {
        // Keep the content at least as large as the viewport so the image is
        // centered when it is smaller than the window, while still allowing the
        // scrollbars to pan when zoomed in.
        contentPane.minWidthProperty().bind(
            Bindings.createDoubleBinding(
                { scrollPane.viewportBounds.width },
                scrollPane.viewportBoundsProperty()
            )
        )
        contentPane.minHeightProperty().bind(
            Bindings.createDoubleBinding(
                { scrollPane.viewportBounds.height },
                scrollPane.viewportBoundsProperty()
            )
        )
        StackPane.setAlignment(minimap, Pos.BOTTOM_RIGHT)
        StackPane.setMargin(minimap, Insets(0.0, 12.0, 12.0, 0.0))
    }

    /**
     * Sizes the bottom-right minimap (the image silhouette) for the current
     * image, matching its aspect ratio. Visibility is decided by
     * [refreshMinimapViewport] so it only appears when panning is possible.
     */
    fun refreshMinimapSize(sourceWidth: Int, sourceHeight: Int) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            hideMinimap()
            return
        }
        val aspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val maxAspect = MINIMAP_MAX_WIDTH / MINIMAP_MAX_HEIGHT
        val width: Double
        val height: Double
        if (aspect > maxAspect) {
            width = MINIMAP_MAX_WIDTH
            height = MINIMAP_MAX_WIDTH / aspect
        } else {
            height = MINIMAP_MAX_HEIGHT
            width = MINIMAP_MAX_HEIGHT * aspect
        }
        minimap.minWidth = width
        minimap.maxWidth = width
        minimap.minHeight = height
        minimap.maxHeight = height
        minimap.prefWidth = width
        minimap.prefHeight = height
        minimapDisplayWidth = width
        minimapDisplayHeight = height
        refreshMinimapViewport()
    }

    /**
     * Positions the minimap's viewport box to mirror the part of the image
     * currently visible.
     *
     * The visible region is derived from the scroll values and the content
     * geometry rather than from scene coordinates: a scroll value changes
     * before the layout pass moves the content, so reading `localToScene` from
     * a scroll listener would always see a stale position (and the box would
     * never follow a drag). The scroll values and content sizes are correct
     * immediately.
     */
    fun refreshMinimapViewport() {
        if (imageView.image == null) {
            hideMinimap()
            return
        }
        val paneWidth = minimapDisplayWidth
        val paneHeight = minimapDisplayHeight
        if (paneWidth <= 1.0 || paneHeight <= 1.0) {
            hideMinimap()
            return
        }

        val imageWidth = imageView.layoutBounds.width
        val imageHeight = imageView.layoutBounds.height
        if (imageWidth <= 0.0 || imageHeight <= 0.0) {
            hideMinimap()
            return
        }

        val viewportWidth = scrollPane.viewportBounds.width
        val viewportHeight = scrollPane.viewportBounds.height
        val contentWidth = contentPane.layoutBounds.width
        val contentHeight = contentPane.layoutBounds.height
        if (contentWidth <= 0.0 || contentHeight <= 0.0) {
            hideMinimap()
            return
        }

        // Scroll offset in content coordinates, and the image's top-left there
        // (the image is centered in the content pane).
        val scrollableWidth = (contentWidth - viewportWidth).coerceAtLeast(0.0)
        val scrollableHeight = (contentHeight - viewportHeight).coerceAtLeast(0.0)
        val offsetX = scrollPane.hvalue * scrollableWidth
        val offsetY = scrollPane.vvalue * scrollableHeight
        val imageX = (contentWidth - imageWidth) / 2.0
        val imageY = (contentHeight - imageHeight) / 2.0

        val left = max(offsetX, imageX)
        val top = max(offsetY, imageY)
        val right = min(offsetX + viewportWidth, imageX + imageWidth)
        val bottom = min(offsetY + viewportHeight, imageY + imageHeight)
        val visibleWidth = right - left
        val visibleHeight = bottom - top
        if (visibleWidth <= 0.0 || visibleHeight <= 0.0) {
            hideMinimap()
            return
        }

        val fractionX = ((left - imageX) / imageWidth).coerceIn(0.0, 1.0)
        val fractionY = ((top - imageY) / imageHeight).coerceIn(0.0, 1.0)
        val fractionWidth = (visibleWidth / imageWidth).coerceIn(0.0, 1.0)
        val fractionHeight = (visibleHeight / imageHeight).coerceIn(0.0, 1.0)

        minimapFractionX = fractionX
        minimapFractionY = fractionY
        minimapFractionWidth = fractionWidth
        minimapFractionHeight = fractionHeight

        // Only show the overview when part of the image is off-screen, i.e.
        // when panning is actually possible (with a small tolerance so it does
        // not flicker on at fit zoom due to rounding).
        val needed = fractionWidth < 0.99 || fractionHeight < 0.99
        minimap.isVisible = needed
        minimapViewport.isVisible = needed
        if (!needed) return

        minimapViewport.x = fractionX * paneWidth
        minimapViewport.y = fractionY * paneHeight
        minimapViewport.width = fractionWidth * paneWidth
        minimapViewport.height = fractionHeight * paneHeight
    }

    fun hideMinimap() {
        minimap.isVisible = false
        minimapViewport.isVisible = false
    }

    /**
     * Starts a minimap drag, recording the offset between the pointer and the
     * center of the viewport box so the box moves naturally under the pointer.
     */
    private fun beginMinimapDrag(event: MouseEvent) {
        if (minimap.width <= 1.0 || minimap.height <= 1.0) return
        val centerX = (minimapFractionX + minimapFractionWidth / 2.0) * minimap.width
        val centerY = (minimapFractionY + minimapFractionHeight / 2.0) * minimap.height
        minimapGrabOffsetX = event.x - centerX
        minimapGrabOffsetY = event.y - centerY
        event.consume()
    }

    private fun dragMinimap(event: MouseEvent) {
        if (minimap.width <= 1.0 || minimap.height <= 1.0) return
        val centerX = event.x - minimapGrabOffsetX
        val centerY = event.y - minimapGrabOffsetY
        panToImageFraction(centerX / minimap.width, centerY / minimap.height)
        event.consume()
    }

    /**
     * Scrolls so that the given image fractions (0..1) are the center of the
     * visible region.
     */
    private fun panToImageFraction(centerFractionX: Double, centerFractionY: Double) {
        val imageWidth = imageView.layoutBounds.width
        val imageHeight = imageView.layoutBounds.height
        val contentWidth = contentPane.layoutBounds.width
        val contentHeight = contentPane.layoutBounds.height
        val viewportWidth = scrollPane.viewportBounds.width
        val viewportHeight = scrollPane.viewportBounds.height
        if (imageWidth <= 0.0 || imageHeight <= 0.0) return

        val scrollableWidth = contentWidth - viewportWidth
        val scrollableHeight = contentHeight - viewportHeight
        if (scrollableWidth > 0.0) {
            val leftFraction = (centerFractionX - minimapFractionWidth / 2.0)
                .coerceIn(0.0, 1.0 - minimapFractionWidth)
            val imageX = (contentWidth - imageWidth) / 2.0
            val offsetX = leftFraction * imageWidth + imageX
            scrollPane.hvalue = (offsetX / scrollableWidth).coerceIn(0.0, 1.0)
        }
        if (scrollableHeight > 0.0) {
            val topFraction = (centerFractionY - minimapFractionHeight / 2.0)
                .coerceIn(0.0, 1.0 - minimapFractionHeight)
            val imageY = (contentHeight - imageHeight) / 2.0
            val offsetY = topFraction * imageHeight + imageY
            scrollPane.vvalue = (offsetY / scrollableHeight).coerceIn(0.0, 1.0)
        }
    }

    companion object {
        // Maximum size of the bottom-right minimap, in logical pixels.
        private const val MINIMAP_MAX_WIDTH = 180.0
        private const val MINIMAP_MAX_HEIGHT = 140.0
    }
}
