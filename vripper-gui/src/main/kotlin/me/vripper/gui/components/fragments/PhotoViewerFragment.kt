package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.animation.FadeTransition
import javafx.animation.PauseTransition
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.value.ChangeListener
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import javafx.scene.input.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.vripper.entities.Status
import me.vripper.gui.components.fragments.PhotoViewerFragment.Companion.RESIZE_RELOAD_RATIO
import me.vripper.gui.controller.ImageController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.ImageModel
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.gui.utils.FastJpegDecoder
import me.vripper.gui.utils.ImageCacheManager
import me.vripper.gui.utils.WidgetSettings
import me.vripper.utilities.LoggerDelegate
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.FX
import tornadofx.Fragment
import tornadofx.runLater
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PhotoViewerFragment : Fragment("Image Viewer") {

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sources: List<ImageSource> by param()
    val initialIndex: Int by param()

    // Used by the refresh action to re-query the current download state.
    private val imageController: ImageController by inject()
    private val widgetsController: WidgetsController by inject()
    private val logger by LoggerDelegate()

    private val indexProperty = SimpleIntegerProperty(
        initialIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
    )

    // True while the thumbnail grid is shown; drives the status bar labels.
    private val browseModeProperty = SimpleBooleanProperty(false)

    // Window geometry persistence. The last non-maximized bounds are tracked
    // while the viewer is open so that closing it while maximized still
    // remembers the size/position to restore when it is un-maximized later.
    private var windowStage: Stage? = null
    private var normalWindowX = 0.0
    private var normalWindowY = 0.0
    private var normalWindowWidth = DEFAULT_WINDOW_WIDTH
    private var normalWindowHeight = DEFAULT_WINDOW_HEIGHT
    private var windowMaximized = false
    private var applyingWindowGeometry = false
    private val windowGeometryListener = ChangeListener<Number> { _, _, _ ->
        val stage = windowStage
        if (stage != null && !applyingWindowGeometry && !windowMaximized) {
            normalWindowX = stage.x
            normalWindowY = stage.y
            normalWindowWidth = stage.width
            normalWindowHeight = stage.height
        }
    }
    private val windowMaximizedListener = ChangeListener<Boolean> { _, _, maximized ->
        if (!applyingWindowGeometry) {
            windowMaximized = maximized
        }
    }

    private val imageView = ImageView().apply {
        isPreserveRatio = true
        isSmooth = true
        // We manage decoded images ourselves; letting the node cache a full
        // resolution render doubles the native texture memory and can crash
        // the JVM on large photos.
        isCache = false
    }

    // Shown centred over the image area when the current image has no data
    // (not downloaded yet or unavailable), instead of a blank transparent image.
    private val emptyLabel = Label("Image not available").apply {
        isVisible = false
        isMouseTransparent = true
        styleClass.add(Styles.TEXT_MUTED)
        style = "-fx-font-size: 16px;"
    }

    /**
     * LRU cache of decoded frames, bounded by total decoded bytes rather than a
     * fixed count (a single HiDPI frame can be tens of times larger than a
     * windowed one). Keeps the decode scale alongside each frame so the deep-zoom
     * logic knows how much detail is already available.
     */
    private class DecodedImageCache(private val maxBytes: Long) {
        private class Frame(val image: Image, val scale: Double, val bytes: Long)

        private val frames = LinkedHashMap<Int, Frame>(16, 0.75f, true)
        private var totalBytes = 0L

        // The index currently on screen; never evicted so the displayed image
        // cannot be dropped by a background preload.
        private var pinned = -1

        @Synchronized
        fun pin(index: Int) {
            pinned = index
        }

        @Synchronized
        fun get(index: Int): Image? = frames[index]?.image

        @Synchronized
        fun scaleOf(index: Int): Double = frames[index]?.scale ?: 0.0

        @Synchronized
        fun contains(index: Int): Boolean = frames.containsKey(index)

        @Synchronized
        fun put(index: Int, image: Image, scale: Double) {
            val bytes = image.width.toLong() * image.height.toLong() * 4L
            val previous = frames.put(index, Frame(image, scale, bytes))
            if (previous != null) totalBytes -= previous.bytes
            totalBytes += bytes
            evict()
        }

        @Synchronized
        fun remove(index: Int) {
            frames.remove(index)?.let { totalBytes -= it.bytes }
        }

        @Synchronized
        fun clear() {
            frames.clear()
            totalBytes = 0L
            pinned = -1
        }

        // Evicts least-recently-used frames, never the pinned one, and always
        // keeps at least one frame so a single image larger than the budget can
        // still be shown without being re-decoded on every access.
        private fun evict() {
            val iterator = frames.entries.iterator()
            while (totalBytes > maxBytes && countEvictable() > 1 && iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key == pinned) continue
                totalBytes -= entry.value.bytes
                iterator.remove()
            }
        }

        private fun countEvictable(): Int =
            frames.size - if (frames.containsKey(pinned)) 1 else 0
    }

    // Client-side viewer preferences (memory bounds and preloading).
    private val viewerSettings = WidgetSettings.loadSettings().imageViewerSettings

    private val decodedCacheBudget: Long = run {
        val configured = viewerSettings.decodedCacheMb.coerceIn(64, 4096).toLong() * 1024 * 1024
        val maxHeap = Runtime.getRuntime().maxMemory()
        if (maxHeap == Long.MAX_VALUE) configured else min(configured, maxHeap / 4)
    }

    private val decodedCache = DecodedImageCache(decodedCacheBudget)

    // Limits how many background downloads/decodes run at once so a wide preload
    // cannot spike CPU or hammer the endpoint.
    private val bytePreloadSemaphore = Semaphore(viewerSettings.bytePreloadConcurrency.coerceIn(1, 8))
    private val decodePreloadSemaphore =
        Semaphore(viewerSettings.decodePreloadConcurrency.coerceIn(1, 4))

    // Caps how many interactive bitmap decodes (navigation + deep zoom) run at
    // the same time, so fast scrolling cannot pile up large allocations. Preload
    // decodes use a separate limit so they never delay the current image.
    private val decodeSemaphore = Semaphore(DECODE_CONCURRENCY)

    // One lock per image index, so a speculative preload and a navigation to the
    // same neighbour share a single decode instead of decoding it twice.
    private val decodeLocks = HashMap<Int, Mutex>()
    private val decodeLocksLock = Any()

    // Intrinsic (real) resolution of each source image, read from the encoded
    // header on a background thread while loading. Used for the window title
    // and to compute the zoom range.
    private val sourceSizes = HashMap<Int, Pair<Int, Int>>()

    // Encoded format and byte size per image, read from the data while loading.
    private data class EncodedInfo(val format: String, val byteSize: Long)

    private val encodedInfos = HashMap<Int, EncodedInfo>()

    private var toolbar: ToolBar
    private var scrollPane: ScrollPane
    private var contentPane: StackPane
    private var centerStack: StackPane
    private var zoomSlider: Slider
    private var fitButton: Button
    private var fillButton: Button
    private var statusLabel: Label
    private var indexLabel: Label
    private var sourceLabel: Label
    private var statusBar: VBox
    private var progressBar: ProgressBar
    private var minimap: Pane
    private var minimapViewport: Rectangle

    // Browse mode: a grid of every image's thumbnail. Double-clicking a
    // thumbnail switches back to view mode focused on that image.
    private var viewToggle: ToggleButton
    private var browseToggle: ToggleButton
    private var browseScrollPane: ScrollPane? = null
    private var browseTilePane: TilePane? = null
    private val thumbCells = ArrayList<VBox>()
    private val thumbViews = ArrayList<ImageView>()
    private val thumbBadges = ArrayList<FontIcon>()
    private val thumbTooltips = ArrayList<Tooltip>()
    private val thumbsRequested = HashSet<Int>()
    private var browseInitialized = false
    private var browseMode = false

    // Currently highlighted tile in browse mode, and the size the thumbnails
    // are rendered at (controlled by the zoom in/out controls while browsing).
    private var browseIndex = 0
    private var thumbSize = THUMB_SIZE

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

    // Current zoom as a fraction of the real resolution (1.0 = 100%). The
    // minimum is the "fit" zoom, the maximum is MAX_ZOOM.
    private var zoom = 1.0
    private var fitZoom = 1.0
    private var updatingZoomControls = false

    // 1x1 fully transparent image used as a sentinel for "no image available".
    // A WritableImage starts out transparent, so no encoded PNG is needed (the
    // previous hard-coded base64 blob had a corrupt IDAT checksum).
    private val missingPlaceholder: Image = WritableImage(1, 1)

    private var navigationJob: Job? = null
    private var latestRequest = 0

    // Index an in-flight navigation is loading, or -1 when nothing is loading.
    // A resize rebuild must reload this index rather than the still-displayed
    // previous one, otherwise enlarging the window mid-navigation would cancel
    // the navigation and bring the old image back.
    private var navigationTarget = -1

    // Set once an image has actually been displayed. Resize rebuilds are gated
    // on this so they cannot race the initial load (whose target is not settled
    // until the first layout pass).
    private var imageShown = false

    // Scope for speculative warm-up work. Kept alive across navigation: stale
    // jobs bail out via latestRequest, while downloads already in flight are
    // allowed to finish. Cancelling them would abort the server-streaming gRPC
    // call mid-transfer and produce a spurious "Stream closed before write"
    // warning on the server. Its job is stable so reload/resize can drop the
    // queued preloads with cancelChildren() instead of recreating the scope.
    private val preloadJob = SupervisorJob()
    private val preloadScope = CoroutineScope(preloadJob + Dispatchers.IO)

    // Set while the user is panning/dragging, so speculative decodes and
    // downloads are kept off the CPU/IO while the render thread needs them.
    @Volatile
    private var preloadSuspended = false

    // Maximum size (device pixels) an image will be displayed at. Written on
    // the FX thread, read while decoding on a background thread.
    @Volatile
    private var decodeWidth = 0.0

    @Volatile
    private var decodeHeight = 0.0

    // Decode target the current cache contents were built for. When the window
    // grows beyond this, cached images are re-decoded at the larger size.
    private var builtForWidth = 0.0
    private var builtForHeight = 0.0

    // High-resolution decode of the currently displayed image. Kept for the
    // current index only so deep zoom cannot pile up full-resolution bitmaps in
    // memory; discarded when navigating away or zooming back to fit.
    private var deepImage: Image? = null
    private var deepIndex = -1
    private var deepScale = 0.0
    private var deepDecodeJob: Job? = null
    private var pendingDeepScale = 0.0

    // Debounce so dragging the zoom slider does not trigger a decode per step.
    private val deepDecodeDebounce = PauseTransition(Duration.millis(150.0)).apply {
        setOnFinished { performDeepDecode() }
    }

    // Debounce so a continuous resize does not trigger a reload on every frame.
    private val resizeDebounce = PauseTransition(Duration.millis(250.0)).apply {
        setOnFinished { rebuildCacheForCurrentSize() }
    }

    // Show the loading bar only if a load takes longer than this, so quick loads
    // (cached images) never flash it.
    private val loadingDebounce = PauseTransition(Duration.millis(300.0)).apply {
        setOnFinished { showLoadingBar() }
    }

    override val root = BorderPane().apply {
        contentPane = StackPane(imageView, emptyLabel).apply {
            alignment = Pos.CENTER
        }
        scrollPane = ScrollPane(contentPane).apply {
            isPannable = true
            isFitToWidth = false
            isFitToHeight = false
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            // Let the StackPane stretch the scroll pane to fill the viewer.
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
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

        // Bottom-right overview: a silhouette of the full image with a box
        // showing the portion of it currently visible in the viewport.
        minimapViewport = Rectangle().apply {
            fill = Color.rgb(255, 255, 255, 0.25)
            stroke = Color.WHITE
            strokeWidth = 1.0
            isManaged = false
        }
        minimap = Pane(minimapViewport).apply {
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

        centerStack = StackPane(scrollPane, minimap).apply {
            alignment = Pos.CENTER
        }
        StackPane.setAlignment(minimap, Pos.BOTTOM_RIGHT)
        StackPane.setMargin(minimap, Insets(0.0, 12.0, 12.0, 0.0))

        center = centerStack
        BorderPane.setAlignment(centerStack, Pos.CENTER)

        val modeGroup = ToggleGroup()
        viewToggle = ToggleButton("View").apply {
            graphic = FontIcon.of(Feather.EYE)
            toggleGroup = modeGroup
            isSelected = true
            tooltip = Tooltip("View the image")
            setOnAction {
                if (!isSelected) {
                    isSelected = true
                } else {
                    setMode(browse = false)
                }
            }
        }
        browseToggle = ToggleButton("Browse").apply {
            graphic = FontIcon.of(Feather.GRID)
            toggleGroup = modeGroup
            tooltip = Tooltip("Browse all thumbnails")
            setOnAction {
                if (!isSelected) {
                    isSelected = true
                } else {
                    setMode(browse = true)
                }
            }
        }

        val prevButton = Button().apply {
            graphic = FontIcon.of(Feather.ARROW_LEFT)
            styleClass.add(Styles.BUTTON_ICON)
            setOnAction { navigate(-1) }
        }

        val nextButton = Button().apply {
            graphic = FontIcon.of(Feather.ARROW_RIGHT)
            styleClass.add(Styles.BUTTON_ICON)
            setOnAction { navigate(1) }
        }

        val zoomOutButton = Button().apply {
            graphic = FontIcon.of(Feather.ZOOM_OUT)
            styleClass.add(Styles.BUTTON_ICON)
            setOnAction { zoomOut() }
        }

        zoomSlider = Slider(fitZoom * 100.0, MAX_ZOOM * 100.0, zoom * 100.0).apply {
            prefWidth = 160.0
            blockIncrement = 10.0
        }

        val zoomInButton = Button().apply {
            graphic = FontIcon.of(Feather.ZOOM_IN)
            styleClass.add(Styles.BUTTON_ICON)
            setOnAction { zoomIn() }
        }

        fitButton = Button("Fit").apply {
            tooltip = Tooltip("Fit to window")
            setOnAction { setZoom(fitZoom) }
        }

        fillButton = Button("Fill").apply {
            tooltip = Tooltip("Fill (100%)")
            setOnAction { setZoom(1.0) }
        }

        val refreshButton = Button().apply {
            graphic = FontIcon.of(Feather.REFRESH_CW)
            styleClass.add(Styles.BUTTON_ICON)
            tooltip = Tooltip("Refresh")
            setOnAction { refresh() }
        }

        toolbar = ToolBar(
            viewToggle,
            browseToggle,
            Separator(Orientation.VERTICAL),
            prevButton,
            nextButton,
            Separator(Orientation.VERTICAL),
            zoomOutButton,
            zoomSlider,
            zoomInButton,
            fitButton,
            fillButton,
            Separator(Orientation.VERTICAL),
            refreshButton
        )
        top = toolbar

        // Bottom status bar: image information on the left, centred position.
        // The info label is capped so it never runs into the centred index.
        statusLabel = Label().apply {
            minWidth = 0.0
            alignment = Pos.CENTER_LEFT
            textOverrun = OverrunStyle.CENTER_ELLIPSIS
            // Image details have no meaning while browsing the grid.
            visibleProperty().bind(browseModeProperty.not())
            managedProperty().bind(browseModeProperty.not())
        }
        indexLabel = Label().apply {
            maxWidth = Region.USE_PREF_SIZE
            alignment = Pos.CENTER
            textProperty().bind(
                Bindings.createStringBinding(
                    {
                        if (browseModeProperty.get()) {
                            if (sources.size == 1) "1 photo" else "${sources.size} photos"
                        } else {
                            "${indexProperty.get() + 1} of ${sources.size}"
                        }
                    },
                    browseModeProperty,
                    indexProperty
                )
            )
        }
        // Source indicator: reassures the user that the displayed pixels come
        // from the downloaded file (local disk or server), never from the
        // web-hosted image. Hidden in browse mode, where tiles are thumbnails.
        sourceLabel = Label().apply {
            maxWidth = Region.USE_PREF_SIZE
            alignment = Pos.CENTER_RIGHT
            style = "-fx-padding: 1 6 1 6; -fx-background-radius: 8; " +
                    "-fx-background-color: rgba(128,128,128,0.16);"
            visibleProperty().bind(browseModeProperty.not())
            managedProperty().bind(browseModeProperty.not())
        }
        // Thin indeterminate bar pinned to the very bottom of the window (below
        // the status info), shown only while an image is taking a while to load
        // (not for quick loads). Its 3px height is always reserved so
        // showing/hiding it never resizes the viewer.
        progressBar = ProgressBar().apply {
            isVisible = false
            // Stretch to the window width, but keep the *preferred* width
            // finite: a MAX_VALUE pref width propagates up through the VBox and
            // BorderPane and makes the whole scene prefer an unbounded width,
            // which sizes the viewer stage to the full display width.
            maxWidth = Double.MAX_VALUE
            minHeight = 3.0
            prefHeight = 3.0
            maxHeight = 3.0
            style = "-fx-padding: 0;"
        }
        // Lay the three parts out in separate regions instead of stacking them:
        // the image info on the left, the position counter in the middle and
        // the source chip on the right can then never overlap, no matter how
        // narrow the window is or how long the file name is.
        val bar = BorderPane().apply {
            padding = Insets(4.0, 10.0, 4.0, 10.0)
            left = statusLabel
            center = indexLabel
            right = sourceLabel
        }
        BorderPane.setAlignment(indexLabel, Pos.CENTER)
        BorderPane.setAlignment(sourceLabel, Pos.CENTER_RIGHT)
        statusLabel.maxWidthProperty().bind(
            Bindings.createDoubleBinding(
                {
                    // Reserve room for the counter and the chip; the info text
                    // ellipsizes when it does not fit.
                    max(0.0, bar.width - indexLabel.width - sourceLabel.width - 60.0)
                },
                bar.widthProperty(),
                indexLabel.widthProperty(),
                sourceLabel.widthProperty()
            )
        )
        val bottomBar = VBox(bar, progressBar).apply {
            style = "-fx-border-color: rgba(128,128,128,0.25); -fx-border-width: 1 0 0 0;"
        }
        bottom = bottomBar
        statusBar = bottomBar

        zoomSlider.valueProperty().addListener { _, _, value ->
            if (updatingZoomControls) return@addListener
            if (browseMode) {
                setThumbSize(value.toDouble())
            } else {
                setZoom(value.toDouble() / 100.0)
            }
        }

        fun scheduleResize() {
            runLater {
                updateDecodeTarget()
                recomputeFit(resetToFit = false)
            }
        }

        // Re-evaluate whenever container size or toolbar size changes
        layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }
        toolbar.layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }
        statusBar.layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }

        // Re-evaluate whenever the image changes (so it works on prev/next)
        imageView.imageProperty().addListener { _, _, _ -> scheduleResize() }

        // Keep the minimap viewport box in sync while panning/zooming.
        scrollPane.hvalueProperty().addListener { _, _, _ -> refreshMinimapViewport() }
        scrollPane.vvalueProperty().addListener { _, _, _ -> refreshMinimapViewport() }
        scrollPane.viewportBoundsProperty().addListener { _, _, _ -> refreshMinimapViewport() }
        imageView.boundsInParentProperty().addListener { _, _, _ -> refreshMinimapViewport() }

        isFocusTraversable = true

        // Keyboard shortcuts (captured before child controls handle them). The
        // navigation and zoom shortcuts dispatch to the active mode: the image
        // viewer, or the thumbnail grid while browsing.
        addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            when (event.code) {
                KeyCode.LEFT -> {
                    navigate(-1)
                    event.consume()
                }

                KeyCode.RIGHT -> {
                    navigate(1)
                    event.consume()
                }

                KeyCode.HOME -> {
                    selectIndex(0)
                    event.consume()
                }

                KeyCode.END -> {
                    if (sources.isNotEmpty()) selectIndex(sources.size - 1)
                    event.consume()
                }

                KeyCode.PLUS, KeyCode.ADD, KeyCode.EQUALS -> {
                    zoomIn()
                    event.consume()
                }

                KeyCode.MINUS, KeyCode.SUBTRACT -> {
                    zoomOut()
                    event.consume()
                }

                KeyCode.DIGIT0 -> {
                    if (browseMode) setThumbSize(THUMB_SIZE) else setZoom(fitZoom)
                    event.consume()
                }

                else -> {}
            }
        }

        // Scroll wheel / trackpad over the image: up -> next, down -> previous.
        // Intercepting on the root (and skipping the toolbar) makes sure the
        // ScrollPane never steals the event so navigation stays consistent
        // regardless of zoom; pan while zoomed via drag.
        addEventFilter(ScrollEvent.SCROLL) { event ->
            // In browse mode the scroll goes to the thumbnail grid.
            if (browseMode) return@addEventFilter
            if (isWithin(event.target, toolbar)) return@addEventFilter
            when {
                event.deltaY > 0 -> navigate(-1)
                event.deltaY < 0 -> navigate(1)
            }
            event.consume()
        }

        // Stop speculative preloading while the user is dragging (panning), so a
        // background decode/download cannot steal CPU from the render thread and
        // make panning stutter. Preloading resumes on release.
        scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED) { suspendPreloads() }
        scrollPane.addEventFilter(MouseEvent.MOUSE_RELEASED) { resumePreloads() }
    }

    private fun suspendPreloads() {
        if (preloadSuspended) return
        preloadSuspended = true
        // Let in-flight downloads finish instead of resetting their gRPC
        // streams; speculative decodes check this flag and are skipped.
    }

    private fun resumePreloads() {
        if (!preloadSuspended) return
        preloadSuspended = false
        preloadAround(indexProperty.get())
    }

    init {
        // The scene has not been laid out yet. Start from the default decode
        // size rather than the full display, which would keep every image
        // decoded at (e.g.) 4K even in a small window. The first layout pass
        // then raises the target and triggers a rebuild if the window is larger.
        builtForWidth = DEFAULT_DECODE_SIZE
        builtForHeight = DEFAULT_DECODE_SIZE
        startLoading()
        val request = ++latestRequest
        // The caller's index may be stale (e.g. the image list changed between
        // opening the table row and creating the viewer).
        val startIndex = if (sources.isEmpty()) -1 else initialIndex.coerceIn(0, sources.lastIndex)
        navigationTarget = startIndex
        navigationJob = coroutineScope.launch {
            if (startIndex < 0) {
                runLater { stopLoading() }
                return@launch
            }
            try {
                val image = loadImageFor(startIndex)
                runLater {
                    if (request != latestRequest) return@runLater
                    showImage(startIndex, image)
                    preloadAround(startIndex)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to load the initial image", e)
                runLater {
                    if (request != latestRequest) return@runLater
                    imageShown = true
                    stopLoading()
                }
            }
        }
    }

    private fun navigate(delta: Int) {
        if (sources.isEmpty()) return
        if (browseMode) {
            // While browsing, next/previous move the highlighted thumbnail
            // instead of loading an image.
            browseIndex = ((browseIndex + delta) % sources.size + sources.size) % sources.size
            highlightBrowseTile()
            scrollBrowseTileIntoView()
            return
        }
        val newIndex = ((indexProperty.get() + delta) % sources.size + sources.size) % sources.size
        navigateTo(newIndex)
    }

    /**
     * Selects an absolute index: highlights the tile while browsing, or opens
     * the image in view mode otherwise.
     */
    private fun selectIndex(index: Int) {
        if (sources.isEmpty()) return
        if (browseMode) {
            browseIndex = index.coerceIn(0, sources.size - 1)
            highlightBrowseTile()
            scrollBrowseTileIntoView()
        } else {
            navigateTo(index)
        }
    }

    private fun navigateTo(newIndex: Int) {
        if (sources.isEmpty()) return
        if (newIndex == indexProperty.get()) return
        navigationTarget = newIndex
        resetDeepImage()
        startLoading()
        val request = ++latestRequest
        // Cancel any in-flight navigation so a slow decode cannot overwrite a
        // newer selection (and so we do not hold several large bitmaps at once).
        navigationJob?.cancel()
        // Do not cancel preloadScope here: in-flight preloads are already
        // downloading from the server, and cancelling them resets the gRPC
        // stream mid-transfer. Queued preloads from older requests bail out on
        // latestRequest; running ones finish and warm the cache.
        // Decode straight at the current zoom (if zoomed in) so the new image is
        // sharp from the first frame rather than blurred-then-sharpened.
        // If the current image is at its fit zoom, the destination is re-fitted
        // as well. Passing that zoom as the required scale would force a
        // full-resolution decode when leaving a small image (whose fit zoom is
        // 1.0) for a much larger one, which then gets badly downscaled by
        // ImageView. A viewport-sized decode is enough for a fit display;
        // updateImageResolution still deep-decodes if the user is zoomed in.
        val atFit = abs(zoom - fitZoom) < 1e-6
        val minScale = if (atFit) 0.0 else desiredDisplayScale()
        navigationJob = coroutineScope.launch {
            val source = sources[newIndex]
            // A background download may have completed since the viewer opened;
            // fetch the file name if it was not known yet. Run it alongside the
            // image load so navigation is not delayed.
            val modelDeferred = if (source.fileName().isBlank()) {
                async { modelsById()[source.imageId] }
            } else {
                null
            }
            val image = loadImageFor(newIndex, minScale = minScale)
            val model = modelDeferred?.await()
            if (!isActive) return@launch
            runLater {
                if (request != latestRequest) return@runLater
                if (model != null) applyModelState(model, source)
                showImage(newIndex, image, resetZoom = false, immediateDeepZoom = true)
            }
            preloadAround(newIndex)
        }
    }

    /**
     * Opens [index] in view mode after selecting it in browse mode. The
     * previously displayed image is cleared immediately so it cannot linger
     * while the selected one is decoded and loaded.
     */
    private fun chooseFromBrowse(index: Int) {
        browseIndex = index
        setMode(browse = false)
    }

    /**
     * Switches between the single-image view mode and the thumbnail grid
     * (browse) mode.
     */
    private fun setMode(browse: Boolean) {
        if (browseMode == browse) return
        browseMode = browse
        browseModeProperty.set(browse)
        viewToggle.isSelected = !browse
        browseToggle.isSelected = browse
        if (browse) {
            if (!browseInitialized) {
                buildBrowsePane()
                browseInitialized = true
            }
            browseIndex = indexProperty.get().coerceIn(0, max(0, sources.size - 1))
            hideMinimap()
            root.center = browseScrollPane
            loadThumbnails()
            applyThumbSize()
            highlightBrowseTile()
            scrollBrowseTileIntoView()
            configureZoomControls(browse = true)
        } else {
            val target = browseIndex
            root.center = centerStack
            configureZoomControls(browse = false)
            if (target in sources.indices && target != indexProperty.get()) {
                imageView.image = null
                emptyLabel.isVisible = false
                navigateTo(target)
            } else if (decodeTargetOutgrewCache()) {
                // The window was enlarged while browsing and the rebuild was
                // deferred; re-decode now that an image is displayed again.
                rebuildCacheForCurrentSize()
            } else {
                refreshMinimapSize()
            }
        }
    }

    /**
     * Points the zoom controls at the current mode: the image zoom while
     * viewing, or the thumbnail size while browsing (fit/fill have no meaning
     * on the grid, so they are hidden).
     */
    private fun configureZoomControls(browse: Boolean) {
        updatingZoomControls = true
        if (browse) {
            zoomSlider.min = MIN_THUMB_SIZE
            zoomSlider.max = MAX_THUMB_SIZE
            zoomSlider.value = thumbSize
            fitButton.isVisible = false
            fillButton.isVisible = false
        } else {
            zoomSlider.min = fitZoom * 100.0
            zoomSlider.max = MAX_ZOOM * 100.0
            zoomSlider.value = zoom * 100.0
            fitButton.isVisible = true
            fillButton.isVisible = true
        }
        updatingZoomControls = false
    }

    private fun zoomIn() {
        if (browseMode) setThumbSize(thumbSize * ZOOM_STEP) else setZoom(zoom * ZOOM_STEP)
    }

    private fun zoomOut() {
        if (browseMode) setThumbSize(thumbSize / ZOOM_STEP) else setZoom(zoom / ZOOM_STEP)
    }

    /**
     * Resizes the thumbnail tiles. Bounded so the grid stays usable, and kept
     * in sync with the zoom slider.
     */
    private fun setThumbSize(newSize: Double) {
        thumbSize = newSize.coerceIn(MIN_THUMB_SIZE, MAX_THUMB_SIZE)
        updatingZoomControls = true
        zoomSlider.value = thumbSize
        updatingZoomControls = false
        applyThumbSize()
    }

    private fun applyThumbSize() {
        thumbViews.forEach { view ->
            view.fitWidth = thumbSize
            view.fitHeight = thumbSize
        }
        val cellWidth = thumbSize + THUMB_CELL_PADDING * 2.0
        thumbCells.forEach { cell -> cell.prefWidth = cellWidth }
    }

    /**
     * Refresh action. In view mode it reloads the current image from scratch; in
     * browse mode it re-checks the download state and retries missing thumbnails,
     * so images/downloads that completed in the background become visible.
     */
    private fun refresh() {
        if (browseMode) refreshBrowse() else refreshView()
    }

    /**
     * Reloads the current image and clears the neighbouring cached frames that
     * were unavailable (the placeholder), so they are retried and can appear if
     * they were downloaded in the meantime. Successfully cached images (and the
     * downloaded bytes) are never dropped.
     */
    private fun refreshView() {
        if (sources.isEmpty()) return
        val index = indexProperty.get()
        if (index !in sources.indices) return
        val count = sources.size
        if (count > 1) {
            clearNeighborIfUnavailable((index + 1) % count)
            clearNeighborIfUnavailable((index - 1 + count) % count)
        }
        // Reload the current image from its (kept) bytes, or download it again
        // when it was unavailable.
        resetDeepImage()
        decodedCache.remove(index)
        val source = sources[index]
        coroutineScope.launch {
            val model = modelsById()[source.imageId]
            runLater {
                if (model != null) applyModelState(model, source)
                reloadCurrent()
            }
        }
    }

    /**
     * Re-reads the current images from the backend, keyed by image id. Used by
     * refresh to pick up file names / download state that changed meanwhile.
     */
    private suspend fun modelsById(): Map<Long, ImageModel> {
        if (sources.isEmpty()) return emptyMap()
        val postId = sources.first().postEntityId
        if (postId < 0) return emptyMap()
        val models = runCatching { imageController.findImages(postId) }.getOrNull() ?: return emptyMap()
        return models.associateBy { it.id }
    }

    /**
     * Applies the refreshed backend state to a source (file name, thumbnail URL
     * and download flag).
     */
    private fun applyModelState(model: ImageModel, source: ImageSource) {
        source.updateFileName(model.filename)
        source.downloaded = model.status == Status.FINISHED.name
        if (source.thumbUrl.isEmpty() && model.thumbUrl.isNotEmpty()) {
            source.thumbUrl = model.thumbUrl
        }
    }

    /**
     * Drops a neighbour's cached frame only when it is the missing placeholder;
     * a real cached image is left untouched.
     */
    private fun clearNeighborIfUnavailable(i: Int) {
        if (decodedCache.get(i) === missingPlaceholder) {
            decodedCache.remove(i)
        }
    }

    /**
     * Reloads the currently displayed image even though the index has not
     * changed.
     */
    private fun reloadCurrent() {
        val index = indexProperty.get()
        if (index !in sources.indices) return
        // A refresh explicitly targets the displayed image, superseding any
        // navigation that was still loading.
        navigationTarget = index
        startLoading()
        val request = ++latestRequest
        navigationJob?.cancel()
        preloadJob.cancelChildren()
        val minScale = desiredDisplayScale()
        navigationJob = coroutineScope.launch {
            val image = loadImageFor(index, minScale = minScale)
            if (!isActive) return@launch
            runLater {
                if (request != latestRequest) return@runLater
                showImage(index, image, animate = false, resetZoom = false)
            }
            preloadAround(index)
        }
    }

    private fun refreshBrowse() {
        retryMissingThumbnails()
        refreshDownloadState()
    }

    /**
     * Re-queries the current images' statuses and updates the download badges,
     * picking up thumbnails that were not available when the viewer opened.
     */
    private fun refreshDownloadState() {
        if (sources.isEmpty()) return
        coroutineScope.launch {
            val byId = modelsById()
            if (byId.isEmpty()) return@launch
            runLater {
                sources.forEachIndexed { index, source ->
                    val model = byId[source.imageId] ?: return@forEachIndexed
                    val hadThumb = source.thumbUrl.isNotEmpty()
                    applyModelState(model, source)
                    thumbBadges.getOrNull(index)?.isVisible = source.downloaded
                    if (!hadThumb && source.thumbUrl.isNotEmpty()) {
                        thumbsRequested.remove(index)
                    }
                    thumbTooltips.getOrNull(index)?.text =
                        source.fileName().ifBlank { "Image ${index + 1}" }
                }
                retryMissingThumbnails()
            }
        }
    }

    /**
     * Allows thumbnails that have not loaded yet to be requested again.
     */
    private fun retryMissingThumbnails() {
        for (index in thumbViews.indices) {
            if (thumbViews[index].image == null) {
                thumbsRequested.remove(index)
            }
        }
        loadThumbnails()
    }

    /**
     * Builds the thumbnail grid. Each tile shows the image thumbnail with its
     * index; double-clicking a tile opens that image in view mode.
     */
    private fun buildBrowsePane() {
        val tile = TilePane().apply {
            hgap = 8.0
            vgap = 8.0
            padding = Insets(12.0)
            prefColumns = 0
            alignment = Pos.TOP_LEFT
        }
        for (index in sources.indices) {
            val source = sources[index]
            val thumbView = ImageView().apply {
                isPreserveRatio = true
                isSmooth = true
                isCache = true
                fitWidth = THUMB_SIZE
                fitHeight = THUMB_SIZE
            }
            // Badge shown when the full-size image is already downloaded.
            val downloadedBadge = FontIcon.of(Feather.CHECK_CIRCLE).apply {
                iconSize = 18
                styleClass.add(Styles.SUCCESS)
                isVisible = source.downloaded
            }
            val thumbBox = StackPane(thumbView, downloadedBadge).apply {
                alignment = Pos.CENTER
                maxWidth = Region.USE_PREF_SIZE
                maxHeight = Region.USE_PREF_SIZE
            }
            StackPane.setAlignment(downloadedBadge, Pos.TOP_RIGHT)
            StackPane.setMargin(downloadedBadge, Insets(2.0))
            val caption = Label("${index + 1}").apply {
                alignment = Pos.CENTER
                maxWidth = Double.MAX_VALUE
            }
            // A not-yet-downloaded image has no filename yet; fall back to a
            // placeholder so the tooltip is never blank. Updated on refresh.
            val cellTooltip = Tooltip(source.fileName().ifBlank { "Image ${index + 1}" })
            val cell = VBox(thumbBox, caption).apply {
                alignment = Pos.CENTER
                spacing = 4.0
                isPickOnBounds = true
                cursor = Cursor.HAND
                prefWidth = THUMB_SIZE + THUMB_CELL_PADDING * 2.0
                style = THUMB_CELL_STYLE
                Tooltip.install(this, cellTooltip)
                setOnMouseClicked { event ->
                    if (event.button == MouseButton.PRIMARY) {
                        if (event.clickCount == 2) {
                            chooseFromBrowse(index)
                            event.consume()
                        } else if (event.clickCount == 1) {
                            browseIndex = index
                            highlightBrowseTile()
                        }
                    }
                }
            }
            thumbViews.add(thumbView)
            thumbBadges.add(downloadedBadge)
            thumbTooltips.add(cellTooltip)
            thumbCells.add(cell)
            tile.children.add(cell)
        }
        browseTilePane = tile
        browseScrollPane = ScrollPane(tile).apply {
            isFitToWidth = true
            isFitToHeight = false
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        }
    }

    /**
     * Loads every thumbnail in the background (bounded concurrency), caching the
     * encoded bytes through [ImageCacheManager]. Each index is requested once.
     * A fixed number of workers drains the queue so a large post does not create
     * one suspended coroutine per image.
     */
    private fun loadThumbnails() {
        val pending = ArrayList<Int>()
        for (index in sources.indices) {
            if (!thumbsRequested.add(index)) continue
            if (sources[index].thumbUrl.isEmpty()) continue
            pending.add(index)
        }
        if (pending.isEmpty()) return
        val next = AtomicInteger()
        repeat(min(THUMB_CONCURRENCY, pending.size)) {
            coroutineScope.launch {
                while (true) {
                    // Stop draining promptly on cancellation (e.g. the viewer
                    // closing) instead of attempting every queued URL.
                    currentCoroutineContext().ensureActive()
                    val position = next.getAndIncrement()
                    if (position >= pending.size) break
                    val index = pending[position]
                    val source = sources[index]
                    val bytes = runCatching {
                        ImageCacheManager.load(source.postEntityId, source.thumbUrl)
                    }.getOrNull() ?: continue
                    if (bytes.isEmpty()) continue
                    currentCoroutineContext().ensureActive()
                    val image = runCatching {
                        ByteArrayInputStream(bytes).use { Image(it) }
                    }.getOrNull() ?: continue
                    runLater {
                        thumbViews.getOrNull(index)?.let { view ->
                            if (view.image == null) view.image = image
                        }
                    }
                }
            }
        }
    }

    /**
     * Marks the highlighted thumbnail as selected (and clears the previous
     * selection). Does not scroll: a plain click must not move the grid.
     */
    private fun highlightBrowseTile() {
        val current = browseIndex
        thumbCells.forEachIndexed { index, cell ->
            cell.style = if (index == current) THUMB_CELL_SELECTED_STYLE else THUMB_CELL_STYLE
        }
    }

    /**
     * Scrolls the highlighted tile into view, and only when it is not already
     * visible; used by keyboard/toolbar navigation, never by a plain click.
     */
    private fun scrollBrowseTileIntoView() {
        val pane = browseScrollPane ?: return
        val current = browseIndex
        runLater {
            val cell = thumbCells.getOrNull(current) ?: return@runLater
            val viewportHeight = pane.viewportBounds.height
            val contentHeight = pane.content.layoutBounds.height
            if (viewportHeight <= 0.0 || contentHeight <= viewportHeight) return@runLater
            val scrollable = contentHeight - viewportHeight
            val offset = pane.vvalue * scrollable
            val cellTop = cell.boundsInParent.minY
            val cellBottom = cell.boundsInParent.maxY
            when {
                cellTop < offset -> pane.vvalue = (cellTop / scrollable).coerceIn(0.0, 1.0)
                cellBottom > offset + viewportHeight ->
                    pane.vvalue = ((cellBottom - viewportHeight) / scrollable).coerceIn(0.0, 1.0)
            }
        }
    }

    private fun showImage(
        index: Int,
        image: Image,
        animate: Boolean = true,
        resetZoom: Boolean = true,
        immediateDeepZoom: Boolean = false
    ) {
        stopLoading()
        imageShown = true
        navigationTarget = -1
        decodedCache.pin(index)
        val displayed = if (deepIndex == index && deepImage != null) deepImage else image
        imageView.image = displayed
        // Show a readable message instead of a blank transparent placeholder,
        // distinguishing "not downloaded yet" from a downloaded file that has
        // since been deleted, moved or renamed.
        val missing = displayed === missingPlaceholder
        emptyLabel.isVisible = missing
        emptyLabel.text = when {
            !missing -> ""
            sources.getOrNull(index)?.downloaded == true -> "Downloaded file is missing or moved"
            else -> "Not downloaded yet"
        }
        indexProperty.set(index)
        refreshMinimapSize()
        // Title and zoom are refreshed once the display size is known.
        recomputeFit(resetToFit = resetZoom, immediateDeepZoom = immediateDeepZoom)

        if (!animate || !widgetsController.currentSettings.imageViewerSettingsModel.fadeAnimation) {
            imageView.opacity = 1.0
            return
        }

        imageView.opacity = 0.0
        FadeTransition(Duration.millis(120.0), imageView).apply {
            toValue = 1.0
            play()
        }
    }

    /**
     * Recomputes the fit zoom for the current viewport and image, then applies
     * the (possibly preserved) zoom. [resetToFit] snaps the zoom back to fit,
     * used when navigating to a different image.
     */
    private fun recomputeFit(resetToFit: Boolean, immediateDeepZoom: Boolean = false) {
        val newFit = computeFitZoom()
        val wasAtFit = abs(zoom - fitZoom) < 1e-6
        fitZoom = newFit
        val target = when {
            resetToFit -> newFit
            wasAtFit -> newFit
            zoom < newFit -> newFit
            else -> zoom
        }
        setZoom(target, immediateDeepZoom)
    }

    private fun setZoom(newZoom: Double, immediateDeepZoom: Boolean = false) {
        val clamped = newZoom.coerceIn(fitZoom, MAX_ZOOM)
        zoom = clamped
        updatingZoomControls = true
        zoomSlider.min = fitZoom * 100.0
        zoomSlider.max = MAX_ZOOM * 100.0
        zoomSlider.value = clamped * 100.0
        updatingZoomControls = false
        applyZoom(immediateDeepZoom)
    }

    private fun applyZoom(immediateDeepZoom: Boolean = false) {
        val size = currentSourceSize()
        if (size != null) {
            val (_, sourceHeight) = size
            imageView.isPreserveRatio = true
            imageView.fitWidth = 0.0
            imageView.fitHeight = (sourceHeight * zoom).coerceAtLeast(1.0)
        }
        refreshTitle()
        updateImageResolution(immediateDeepZoom)
        refreshMinimapViewport()
        refreshStatusBar()
    }

    /**
     * Sizes the bottom-right minimap (the image silhouette) for the current
     * image, matching its aspect ratio. Visibility is decided by
     * [refreshMinimapViewport] so it only appears when panning is possible.
     */
    private fun refreshMinimapSize() {
        val size = currentSourceSize()
        if (size == null) {
            hideMinimap()
            return
        }
        val (sourceWidth, sourceHeight) = size
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
    private fun refreshMinimapViewport() {
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

    private fun hideMinimap() {
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

    /**
     * Swaps in a higher-resolution decode of the current image while zoomed in
     * (and back to the cached window-sized one when zoomed out), so deep zoom
     * stays crisp without ever holding more than one extra bitmap.
     */
    private fun updateImageResolution(immediate: Boolean = false) {
        val index = indexProperty.get()
        if (index < 0 || sources.isEmpty()) return
        // Before the first layout the fit zoom is unknown (computeFitZoom falls
        // back to 100%), so requesting a deep decode here would decode the full
        // source resolution. The next layout pass recomputes the real fit zoom.
        if (root.width <= 1.0 || root.height <= 1.0) return
        val desired = desiredDisplayScale()
        if (desired <= 0.0) return

        val baseScale = decodedCache.scaleOf(index)
        if (desired > baseScale * DEEP_ZOOM_RATIO) {
            // Deeper detail is needed than the cached image has: decode a
            // high-resolution copy unless we already have a good enough one.
            if (deepIndex == index && deepScale >= desired * 0.99) return
            requestDeepDecode(desired, immediate)
        } else {
            // The cached image is enough: cancel any pending decode (so a stale
            // one cannot land after zooming back out) and release the
            // high-resolution copy.
            pendingDeepScale = 0.0
            deepDecodeDebounce.stop()
            if (deepIndex == index) {
                deepDecodeJob?.cancel()
                deepImage = null
                deepIndex = -1
                deepScale = 0.0
                decodedCache.get(index)?.let { imageView.image = it }
            }
        }
    }

    /**
     * The scale (fraction of the real resolution) needed to render the current
     * image crisply at the current zoom, never exceeding the source.
     */
    private fun desiredDisplayScale(): Double {
        val outputScale = root.scene?.window?.outputScaleX?.takeIf { it > 0.0 } ?: 1.0
        return min(1.0, zoom * outputScale)
    }

    private fun requestDeepDecode(scale: Double, immediate: Boolean = false) {
        pendingDeepScale = scale
        if (immediate) {
            // Navigation already knows the target zoom, so start the high-res
            // decode right away instead of waiting out the slider debounce.
            deepDecodeDebounce.stop()
            performDeepDecode()
        } else {
            deepDecodeDebounce.playFromStart()
        }
    }

    private fun performDeepDecode() {
        val index = indexProperty.get()
        val scale = pendingDeepScale
        if (index < 0 || scale <= 0.0) return
        deepDecodeJob?.cancel()
        deepDecodeJob = coroutineScope.launch {
            val image = decodeAtScale(index, scale)
            if (!isActive) return@launch
            runLater {
                if (indexProperty.get() != index) return@runLater
                if (image === missingPlaceholder) return@runLater
                if (pendingDeepScale < scale * 0.99) return@runLater
                if (deepScale >= scale) return@runLater
                deepImage = image
                deepIndex = index
                deepScale = scale
                imageView.image = image
                applyZoom()
            }
        }
    }

    private fun resetDeepImage() {
        deepDecodeDebounce.stop()
        deepDecodeJob?.cancel()
        deepDecodeJob = null
        deepImage = null
        deepIndex = -1
        deepScale = 0.0
        pendingDeepScale = 0.0
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
        val toolbarH = toolbar.layoutBounds.height
        val statusH = statusBar.layoutBounds.height
        val availableWidth = root.width - SCROLLBAR_ALLOWANCE
        val availableHeight = root.height - toolbarH - statusH - SCROLLBAR_ALLOWANCE
        if (availableWidth <= 1.0 || availableHeight <= 1.0) return 1.0
        return min(1.0, min(availableWidth / sourceWidth, availableHeight / sourceHeight))
    }

    /**
     * Real resolution of the currently displayed image, falling back to the
     * decoded image size when the header could not be parsed.
     */
    private fun currentSourceSize(): Pair<Int, Int>? {
        // The 1x1 placeholder is not a real image: report no size so the title,
        // status bar and minimap do not show "1 × 1".
        if (imageView.image === missingPlaceholder) return null
        val index = indexProperty.get()
        synchronized(sourceSizes) { sourceSizes[index] }?.let { return it }
        val img = imageView.image ?: return null
        val width = img.width.toInt()
        val height = img.height.toInt()
        return if (width > 0 && height > 0) width to height else null
    }

    /**
     * Sets the window title from the currently displayed image: file name, its
     * real resolution and the zoom percentage. Reading the source resolution
     * was already done on a background thread while loading, so this is cheap
     * and always matches the image currently on screen.
     */
    private fun refreshTitle() {
        val index = indexProperty.get()
        val fileName = sources.getOrNull(index)?.fileName().orEmpty()
        if (imageView.image === missingPlaceholder) {
            title = fileName.ifBlank { "Image Viewer" }
            return
        }
        val size = currentSourceSize()
        if (size == null || fileName.isBlank()) {
            title = fileName.ifBlank { "Image Viewer" }
            return
        }
        val (sourceWidth, sourceHeight) = size
        val percent = (zoom * 100.0).roundToInt()
        title = "$fileName  •  $sourceWidth × $sourceHeight  •  $percent%"
    }

    /**
     * Updates the bottom status bar with the current image's name, dimensions,
     * format and file size.
     */
    private fun refreshStatusBar() {
        refreshSourceLabel()
        val index = indexProperty.get()
        val fileName = sources.getOrNull(index)?.fileName()
        if (fileName.isNullOrBlank()) {
            statusLabel.text = ""
            return
        }
        // With no image there is nothing meaningful to report beyond the name;
        // the source chip already explains why it is unavailable.
        if (imageView.image === missingPlaceholder) {
            statusLabel.text = fileName
            return
        }
        val size = currentSourceSize()
        val dimensions = if (size != null && size.first > 0 && size.second > 0) {
            "${size.first} × ${size.second}"
        } else {
            "?"
        }
        val info = synchronized(encodedInfos) { encodedInfos[index] }
        val format = info?.format ?: "?"
        val byteSize = info?.byteSize?.let { formatSize(it) } ?: "?"
        statusLabel.text = "$fileName  •  $dimensions  •  $format  •  $byteSize"
    }

    /**
     * Tells the user where the displayed pixels come from. The viewer only ever
     * reads the downloaded file (local disk or the server, streamed over gRPC);
     * the web-hosted image is never shown in view mode, only as a browse-mode
     * thumbnail.
     */
    private fun refreshSourceLabel() {
        val source = sources.getOrNull(indexProperty.get())
        val missing = imageView.image === missingPlaceholder
        // `downloaded` mirrors the persisted status: true means the download
        // completed at some point. If the bytes are nevertheless unavailable,
        // the file has been deleted or moved since then, which is a different
        // situation from "not downloaded yet".
        val wasDownloaded = source?.downloaded == true
        val local = AppEndpointManager.currentAppState() != GuiEventBus.RemoteSession
        val location = if (local) "on this computer" else "on the server"
        val icon: Feather
        val text: String
        val explanation: String
        if (missing && wasDownloaded) {
            icon = Feather.ALERT_TRIANGLE
            text = "Missing or moved"
            explanation = "This image was downloaded, but the file is no longer " +
                    "available $location. It may have been deleted, moved or renamed."
        } else if (missing) {
            icon = Feather.CLOUD_OFF
            text = "Not downloaded"
            explanation = "This image has not been downloaded yet, so there is no " +
                    "local or server copy to show. The web-hosted image is not used here."
        } else if (local) {
            icon = Feather.HARD_DRIVE
            text = "Downloaded · local"
            explanation = "Viewing the downloaded file stored on this computer, " +
                    "not the image hosted on the web."
        } else {
            icon = Feather.SERVER
            text = "Downloaded · server"
            explanation = "Viewing the file downloaded on the server and streamed " +
                    "over gRPC, not the image hosted on the web."
        }
        if (sourceLabel.text == text) return
        sourceLabel.text = text
        sourceLabel.graphic = FontIcon(icon).apply { iconSize = 12 }
        sourceLabel.tooltip = Tooltip(explanation)
    }

    /**
     * Starts the loading indicator. The bar is only shown if the load is still
     * running after a short delay, so fast (cached) loads never flash it.
     */
    private fun startLoading() {
        loadingDebounce.playFromStart()
    }

    private fun stopLoading() {
        loadingDebounce.stop()
        progressBar.isVisible = false
    }

    private fun showLoadingBar() {
        progressBar.isVisible = true
    }

    private fun formatSize(bytes: Long): String {
        val kb = 1024.0
        return when {
            bytes < kb -> "$bytes B"
            bytes < kb * kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            bytes < kb * kb * kb -> String.format(Locale.US, "%.2f MB", bytes / (kb * kb))
            else -> String.format(Locale.US, "%.2f GB", bytes / (kb * kb * kb))
        }
    }

    /**
     * Detects the actual image format from the encoded data (not the file
     * extension), falling back to magic-byte sniffing for formats ImageIO does
     * not know (e.g. WebP).
     */
    private fun detectFormat(bytes: ByteArray): String = runCatching {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes))?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (readers.hasNext()) {
                val reader = readers.next()
                try {
                    return@runCatching reader.formatName.uppercase()
                } finally {
                    reader.dispose()
                }
            }
        }
        formatFromMagic(bytes)
    }.getOrElse { formatFromMagic(bytes) }

    private fun formatFromMagic(bytes: ByteArray): String {
        fun at(i: Int): Int = bytes[i].toInt() and 0xff
        return when {
            bytes.size >= 3 && at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "JPEG"
            bytes.size >= 8 && at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "PNG"
            bytes.size >= 6 && at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "GIF"
            bytes.size >= 2 && at(0) == 0x42 && at(1) == 0x4D -> "BMP"
            bytes.size >= 12 && at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                    at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "WEBP"

            else -> "?"
        }
    }

    /**
     * Maximum size, in device pixels, an image will ever be displayed at.
     * Decoding to this size keeps native texture memory bounded regardless of
     * the source resolution while remaining crisp for the current display.
     * Must be called on the FX thread.
     */
    private fun updateDecodeTarget() {
        val w = root.width
        val h = root.height
        // No layout yet: image loads fall back to DEFAULT_DECODE_SIZE. Do not
        // ratchet the target up to the full screen before the window has a size.
        if (w <= 1.0 || h <= 1.0) return
        val window = root.scene?.window
        val outputScale = window?.outputScaleX?.takeIf { it > 0.0 } ?: 1.0
        val targetWidth = w * outputScale
        val targetHeight = h * outputScale

        // Ratchet: never decode below the largest size we have needed so far.
        // Otherwise images loaded while the window is small would stay small
        // after it is enlarged again.
        if (targetWidth > decodeWidth) decodeWidth = targetWidth
        if (targetHeight > decodeHeight) decodeHeight = targetHeight

        // If the window grew, the cached images may now be too small: re-decode
        // them once the resize settles.
        if (decodeTargetOutgrewCache()) {
            resizeDebounce.playFromStart()
        }
    }

    /**
     * Whether the (ratcheted) decode target now exceeds the size the cached
     * frames were built for by more than [RESIZE_RELOAD_RATIO].
     */
    private fun decodeTargetOutgrewCache(): Boolean =
        builtForWidth > 0.0 && (decodeWidth > builtForWidth * RESIZE_RELOAD_RATIO ||
                decodeHeight > builtForHeight * RESIZE_RELOAD_RATIO)

    /**
     * Re-decodes the currently displayed image (and its neighbours) at the
     * current, larger decode target. Called after a resize stops growing.
     */
    private fun rebuildCacheForCurrentSize() {
        if (sources.isEmpty()) return
        // In browse mode nothing from the decoded-frame cache is displayed:
        // rebuilding it would only clear/warm bitmaps in the background.
        if (browseMode) return
        // Do not race the initial load: until an image has been shown the
        // target is still being settled by the first layout pass. A rebuild is
        // scheduled again once the image is displayed.
        if (!imageShown) return
        if (decodeWidth <= 0.0 || decodeHeight <= 0.0) return
        // A navigation may still be loading (indexProperty lags until the image
        // is shown): rebuild its target, not the image still on screen.
        val current = if (navigationTarget in sources.indices) navigationTarget else indexProperty.get()
        navigationTarget = current
        builtForWidth = decodeWidth
        builtForHeight = decodeHeight
        decodedCache.clear()

        val request = ++latestRequest
        // Read the display scale on the FX thread, like the other load paths.
        val minScale = desiredDisplayScale()
        navigationJob?.cancel()
        preloadJob.cancelChildren()
        navigationJob = coroutineScope.launch {
            val source = sources[current]
            // A rebuild may replace an in-flight navigation, so pick up the
            // file name / download state it would have refreshed.
            val modelDeferred = if (source.fileName().isBlank()) {
                async { modelsById()[source.imageId] }
            } else {
                null
            }
            val image = loadImageFor(current, minScale = minScale)
            val model = modelDeferred?.await()
            if (!isActive) return@launch
            runLater {
                if (request != latestRequest) return@runLater
                if (model != null) applyModelState(model, source)
                showImage(current, image, animate = false, resetZoom = false)
            }
            preloadAround(current)
        }
    }

    /**
     * Warms up the neighbours of [current] in the background: the immediate
     * neighbours are fully decoded (so prev/next is instant), while a wider
     * window only pre-loads the encoded bytes into [ImageCacheManager] so a
     * later navigation is a local decode instead of a transfer.
     */
    private fun preloadAround(current: Int) {
        if (preloadSuspended) return
        if (sources.isEmpty()) return
        val count = sources.size
        // Decode the immediate neighbours first: they are the ones the user is
        // most likely to reach, and they must not queue behind byte downloads.
        preloadDecoded((current + 1) % count)
        preloadDecoded((current - 1 + count) % count)
        val radius = viewerSettings.bytePreloadRadius.coerceIn(0, 20)
        for (offset in 2..radius) {
            preloadBytes((current + offset) % count)
            preloadBytes((current - offset + count) % count)
        }
    }

    /**
     * Decodes a neighbour into the decoded-frame cache, bounded by the
     * configured decode-preload concurrency so preloading never serialises the
     * CPU.
     */
    private fun preloadDecoded(i: Int) {
        if (i == indexProperty.get()) return
        if (decodedCache.contains(i)) return
        val request = latestRequest
        preloadScope.launch {
            decodePreloadSemaphore.withPermit {
                if (request != latestRequest) return@launch
                if (i == indexProperty.get() || decodedCache.contains(i)) return@launch
                loadImageFor(i, speculative = true)
            }
        }
    }

    /**
     * Fetches and caches only the encoded bytes of a neighbour (no decode),
     * bounded by the configured byte-preload concurrency.
     */
    private fun preloadBytes(i: Int) {
        // In local mode the file is read straight from disk on demand: there is
        // no transfer to pre-warm, so this would only waste I/O.
        if (AppEndpointManager.currentAppState() != GuiEventBus.RemoteSession) return
        if (i == indexProperty.get()) return
        val request = latestRequest
        preloadScope.launch {
            bytePreloadSemaphore.withPermit {
                if (request != latestRequest) return@launch
                if (i == indexProperty.get()) return@launch
                try {
                    obtainBytes(i)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Preloading is best-effort: a failed transfer must not
                    // surface as an uncaught coroutine exception.
                    logger.debug("Failed to preload bytes for image {}", i, e)
                }
            }
        }
    }

    private suspend fun loadImageFor(
        i: Int,
        speculative: Boolean = false,
        minScale: Double = 0.0
    ): Image {
        // Reuse a cached frame only when it already has at least the detail we
        // need. Otherwise (e.g. navigating while zoomed into a neighbour that was
        // only preloaded at viewport resolution) we decode at the required scale
        // straight away instead of showing a blurred frame that is replaced a
        // moment later.
        val cachedScale = decodedCache.scaleOf(i)
        val cached = decodedCache.get(i)
        if (cached != null && cachedScale >= minScale * 0.99) {
            return cached
        }

        // Serialise decodes of the same index. A speculative preload and a
        // navigation to the same neighbour would otherwise both decode the
        // (expensive) image; the second caller now re-checks the cache after
        // waiting and reuses the first result.
        return decodeLockFor(i).withLock {
            val freshScale = decodedCache.scaleOf(i)
            val fresh = decodedCache.get(i)
            if (fresh != null && freshScale >= minScale * 0.99) {
                fresh
            } else {
                decodeImageFor(i, speculative, minScale)
            }
        }
    }

    private fun decodeLockFor(i: Int): Mutex = synchronized(decodeLocksLock) {
        decodeLocks.getOrPut(i) { Mutex() }
    }

    /**
     * The actual (expensive) fetch + decode of one image. Only ever runs once at
     * a time per index; [loadImageFor] deduplicates callers.
     */
    private suspend fun decodeImageFor(
        i: Int,
        speculative: Boolean,
        minScale: Double
    ): Image {
        // Do not start a new speculative download once the user is panning.
        // Skip without caching, so it is retried when preloading resumes.
        if (speculative && preloadSuspended) return missingPlaceholder
        try {
            val data = obtainBytes(i)
            if (data == null || data.isEmpty()) {
                decodedCache.put(i, missingPlaceholder, 1.0)
                return missingPlaceholder
            }
            val sourceSize = readSourceSize(data)
            if (sourceSize != null) {
                synchronized(sourceSizes) { sourceSizes[i] = sourceSize }
            }
            val info = EncodedInfo(detectFormat(data), data.size.toLong())
            synchronized(encodedInfos) { encodedInfos[i] = info }
            val viewportWidth = if (decodeWidth > 0.0) decodeWidth else DEFAULT_DECODE_SIZE
            val viewportHeight = if (decodeHeight > 0.0) decodeHeight else DEFAULT_DECODE_SIZE
            // Raise the decode target to the current zoom so a zoomed view is sharp
            // from the first frame.
            val zoomWidth = (sourceSize?.first ?: 0) * minScale
            val zoomHeight = (sourceSize?.second ?: 0) * minScale
            val (width, height) = decodeSize(
                sourceSize,
                max(viewportWidth, zoomWidth),
                max(viewportHeight, zoomHeight)
            )
            val scale = if (sourceSize != null && sourceSize.first > 0) width / sourceSize.first else 1.0
            // If this job was cancelled (fast scrolling) while it was fetching the
            // bytes, do not spend CPU on the expensive decode.
            currentCoroutineContext().ensureActive()
            // The user may have started panning while this speculative job was
            // fetching: let the download finish (cancelling it would reset the gRPC
            // stream) but skip the expensive decode.
            if (speculative && preloadSuspended) return missingPlaceholder
            // Speculative (preload) decodes are bounded separately and must not take
            // a permit from this semaphore: otherwise a slow decode of a large
            // neighbour would delay the image the user is actually looking at.
            val image = if (speculative) {
                decodeScaled(data, width, height)
            } else {
                decodeSemaphore.withPermit {
                    // A job cancelled while queued for a permit must not start
                    // the expensive decode after it finally gets one.
                    currentCoroutineContext().ensureActive()
                    decodeScaled(data, width, height)
                }
            }
            decodedCache.put(i, image, scale)
            return image
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed read or decode must not escape as an uncaught coroutine
            // exception (which would leave the viewer empty with a spinning
            // progress bar). Show the same placeholder as an unavailable file;
            // Refresh retries it.
            if (speculative) {
                logger.debug("Failed to preload image {}", i, e)
            } else {
                logger.warn("Failed to load image {}", i, e)
            }
            decodedCache.put(i, missingPlaceholder, 1.0)
            return missingPlaceholder
        }
    }

    /**
     * Decodes a single image at the given scale (fraction of its real
     * resolution) for deep-zoom display. The result is not put in the shared
     * cache; the caller owns it.
     */
    private suspend fun decodeAtScale(i: Int, scale: Double): Image {
        try {
            val bytes = obtainBytes(i)
            if (bytes == null || bytes.isEmpty()) return missingPlaceholder
            val sourceSize = readSourceSize(bytes)
            synchronized(sourceSizes) {
                if (sourceSize != null) sourceSizes[i] = sourceSize
            }
            val (width, height) =
                if (sourceSize != null && sourceSize.first > 0 && sourceSize.second > 0) {
                    (sourceSize.first * scale).coerceAtLeast(1.0) to
                            (sourceSize.second * scale).coerceAtLeast(1.0)
                } else {
                    decodeSize(
                        null,
                        if (decodeWidth > 0.0) decodeWidth else DEFAULT_DECODE_SIZE,
                        if (decodeHeight > 0.0) decodeHeight else DEFAULT_DECODE_SIZE
                    )
                }
            currentCoroutineContext().ensureActive()
            return decodeSemaphore.withPermit {
                // Skip work for jobs that were cancelled while waiting for a
                // permit (e.g. rapid zoom changes).
                currentCoroutineContext().ensureActive()
                decodeScaled(bytes, width, height)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to decode image {} at scale {}", i, scale, e)
            return missingPlaceholder
        }
    }

    /**
     * Decodes and resizes an image to the given display size.
     *
     * JavaFX's own scaling is not used on purpose: its minifier is a fixed
     * integer box filter centered on `floor(dx * scale)`, which is phase
     * misaligned and leaves stair/moiré artifacts on fine detail such as hair
     * when downscaling (and nearest-neighbour when upscaling, JDK-8090523).
     * Instead the image is decoded with ImageIO and resized with a progressive
     * high-quality bilinear filter (repeated halving), which is a good
     * approximation of an area-average low-pass filter and stays crisp.
     *
     * Falls back to JavaFX decoding for formats ImageIO cannot read (e.g. WebP).
     */
    private fun decodeScaled(bytes: ByteArray, targetWidth: Double, targetHeight: Double): Image {
        decodeWithLibJpegTurbo(bytes, targetWidth, targetHeight)?.let { return it }
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        if (stream != null) {
            stream.use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (readers.hasNext()) {
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val sourceWidth = reader.getWidth(0)
                        val sourceHeight = reader.getHeight(0)
                        if (sourceWidth > 0 && sourceHeight > 0) {
                            // Never upscale: clamp the target to the intrinsic size.
                            val width = min(targetWidth, sourceWidth.toDouble()).roundToInt().coerceAtLeast(1)
                            val height = min(targetHeight, sourceHeight.toDouble()).roundToInt().coerceAtLeast(1)
                            val buffered =
                                readSubsampled(reader, sourceWidth, sourceHeight, width, height)
                            if (buffered != null) {
                                val scaled = scaleDownHighQuality(buffered, width, height)
                                return toFxImage(scaled)
                            }
                            logger.debug("Subsampled image read failed, using the JavaFX fallback")
                        }
                    } catch (e: Exception) {
                        logger.debug("ImageIO decode failed, using the JavaFX fallback", e)
                    } finally {
                        runCatching { reader.dispose() }
                    }
                } else {
                    logger.debug("No ImageIO reader found, using the JavaFX fallback")
                }
            }
        }
        return Image(ByteArrayInputStream(bytes), targetWidth, targetHeight, true, true)
    }

    /**
     * JPEG fast path: libjpeg-turbo decodes at 1/2, 1/4 or 1/8 resolution,
     * skipping most of the IDCT work. Returns null for non-JPEG data, when the
     * native library is unavailable, or when decoding fails, so the caller can
     * fall back to the generic ImageIO path below.
     */
    private fun decodeWithLibJpegTurbo(
        bytes: ByteArray,
        targetWidth: Double,
        targetHeight: Double
    ): Image? {
        if (!FastJpegDecoder.isJpeg(bytes)) return null
        val result = FastJpegDecoder.decode(
            bytes,
            targetWidth.roundToInt(),
            targetHeight.roundToInt()
        ) ?: return null
        val width = min(targetWidth, result.image.width.toDouble()).roundToInt().coerceAtLeast(1)
        val height = min(targetHeight, result.image.height.toDouble()).roundToInt().coerceAtLeast(1)
        return toFxImage(scaleDownHighQuality(result.image, width, height))
    }

    /**
     * Decodes [reader]'s first image at a reduced resolution directly, instead
     * of decoding the full-resolution bitmap and then downscaling it. The
     * subsampling step is a power of two (the JPEG/PNG/GIF readers all support
     * at least 1/2/4/8) chosen so the decoded bitmap is still at least the
     * target size, and the small remainder is removed by [scaleDownHighQuality].
     * This is the dominant win for large images: a 6000x4000 JPEG targeting a
     * 2048px viewport decodes ~4x fewer pixels and allocates no full-size
     * intermediate buffers.
     */
    private fun readSubsampled(
        reader: ImageReader,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): BufferedImage? {
        val step = subsamplingStep(sourceWidth, sourceHeight, targetWidth, targetHeight)
        return if (step > 1) {
            runCatching {
                val param = reader.defaultReadParam ?: ImageReadParam()
                param.setSourceSubsampling(step, step, 0, 0)
                reader.read(0, param)
            }.getOrNull()
        } else {
            runCatching { reader.read(0) }.getOrNull()
        }
    }

    private fun subsamplingStep(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var step = 1
        while (step < MAX_SUBSAMPLING &&
            sourceWidth / (step * 2) >= targetWidth &&
            sourceHeight / (step * 2) >= targetHeight
        ) {
            step *= 2
        }
        return step
    }

    /**
     * Downsamples [source] to [targetWidth] x [targetHeight] by repeatedly
     * halving with bilinear interpolation before a final step. Each step works
     * on at most a 2x reduction, which avoids the aliasing a single large-scale
     * bilinear step would produce. The source is usually already close to the
     * target because of source subsampling, so only one or two steps run.
     */
    private fun scaleDownHighQuality(
        source: BufferedImage,
        targetWidth: Int,
        targetHeight: Int
    ): BufferedImage {
        if (targetWidth >= source.width && targetHeight >= source.height) return source

        val hasAlpha = source.colorModel.hasAlpha()
        val type = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB

        var current = source
        var width = source.width
        var height = source.height
        while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
            width /= 2
            height /= 2
            current = drawScaled(current, width, height, type)
        }
        if (width != targetWidth || height != targetHeight) {
            current = drawScaled(current, targetWidth, targetHeight, type)
        }
        return current
    }

    private fun drawScaled(source: BufferedImage, width: Int, height: Int, type: Int): BufferedImage {
        val dest = BufferedImage(width, height, type)
        val graphics = dest.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return dest
    }

    private fun toFxImage(source: BufferedImage): Image {
        val width = source.width
        val height = source.height
        val image = WritableImage(width, height)
        // TYPE_INT_ARGB stores exactly the non-premultiplied ARGB words the
        // pixel writer wants, so hand over the backing array directly instead
        // of copying it through an intermediate IntArray.
        val data = (source.raster.dataBuffer as? DataBufferInt)?.data
        if (source.type == BufferedImage.TYPE_INT_ARGB && data != null && data.size >= width * height) {
            image.pixelWriter.setPixels(
                0, 0, width, height, PixelFormat.getIntArgbInstance(), data, 0, width
            )
        } else {
            val pixels = IntArray(width * height)
            source.getRGB(0, 0, width, height, pixels, 0, width)
            image.pixelWriter.setPixels(
                0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width
            )
        }
        return image
    }

    /**
     * Returns the encoded bytes for an image.
     *
     * Only bytes streamed over gRPC are put in the persistent
     * [ImageCacheManager], so a deep-zoom re-decode (or reopening the viewer)
     * does not transfer the file over gRPC again. In local mode the full-size
     * file already lives on this machine: caching a second copy would double
     * the disk usage (and could go stale) without any I/O benefit.
     */
    private suspend fun obtainBytes(i: Int): ByteArray? {
        val source = sources[i]
        val read: suspend () -> ByteArray? = { source.readBytes() }
        if (AppEndpointManager.currentAppState() != GuiEventBus.RemoteSession) {
            return read()
        }
        return ImageCacheManager.load(source.cacheKey(), read)
    }

    /**
     * Computes the size (device pixels) to decode at, never exceeding the
     * intrinsic size of the source image.
     */
    private fun decodeSize(
        sourceSize: Pair<Int, Int>?,
        targetWidth: Double,
        targetHeight: Double
    ): Pair<Double, Double> {
        if (sourceSize == null) return targetWidth to targetHeight
        val (sourceWidth, sourceHeight) = sourceSize
        if (sourceWidth <= 0 || sourceHeight <= 0) return targetWidth to targetHeight
        val scale = min(1.0, min(targetWidth / sourceWidth, targetHeight / sourceHeight))
        return (sourceWidth * scale).coerceAtLeast(1.0) to (sourceHeight * scale).coerceAtLeast(1.0)
    }

    /**
     * Reads the intrinsic dimensions from the encoded image header without
     * decoding the full bitmap.
     */
    private fun readSourceSize(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        val imageInputStream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: return@runCatching null
        imageInputStream.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@runCatching null
            val reader = readers.next()
            try {
                reader.input = stream
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    private fun isWithin(target: Any?, ancestor: Node): Boolean {
        var node = target as? Node
        while (node != null) {
            if (node === ancestor) return true
            node = node.parent
        }
        return false
    }

    /**
     * Restores the viewer window size/position (and maximized state) saved in
     * the widget settings.
     *
     * When the viewer was maximized last time we deliberately show it windowed
     * first, with the saved normal bounds, then maximize after a short delay.
     * That lets the window manager record the correct restore size instead of
     * deriving it from whatever transient geometry the maximized startup had.
     */
    private fun applySavedWindowGeometry(stage: Stage) {
        applyingWindowGeometry = true
        try {
            stage.minWidth = MIN_WINDOW_SIZE
            stage.minHeight = MIN_WINDOW_SIZE

            val settings = widgetsController.currentSettings.imageViewerSettingsModel
            stage.width = saneWindowSize(settings.windowWidth, DEFAULT_WINDOW_WIDTH)
            stage.height = saneWindowSize(settings.windowHeight, DEFAULT_WINDOW_HEIGHT)

            val x = settings.windowX
            val y = settings.windowY
            // Only restore the position if it is still on a connected screen
            // (the monitor layout may have changed since it was saved).
            if (x.isFinite() && y.isFinite() && Screen.getScreens().any {
                    it.visualBounds.intersects(x, y, MIN_WINDOW_SIZE, MIN_WINDOW_SIZE)
                }) {
                stage.x = x
                stage.y = y
            } else {
                centerOnMainWindow(stage)
            }

            normalWindowX = stage.x
            normalWindowY = stage.y
            normalWindowWidth = stage.width
            normalWindowHeight = stage.height
            windowMaximized = settings.windowMaximized
        } finally {
            applyingWindowGeometry = false
        }

        if (windowMaximized) {
            // Show the window windowed for a moment first so the window manager
            // registers the normal (restore) geometry before we maximize it.
            PauseTransition(Duration.millis(INITIAL_MAXIMIZE_DELAY_MS)).apply {
                setOnFinished {
                    if (windowStage === stage) {
                        stage.isMaximized = true
                    }
                }
                play()
            }
        }
    }

    /**
     * The viewer is opened without an owner so that it can be minimized
     * independently (JavaFX does not allow iconifying owned stages), which also
     * means TornadoFX no longer centers it over the main window. Center it
     * ourselves the first time, when no saved position is available.
     */
    private fun centerOnMainWindow(stage: Stage) {
        val mainStage = FX.getPrimaryStage(scope)
        val screen = mainStage?.let { main ->
            val centerX = main.x + main.width / 2
            val centerY = main.y + main.height / 2
            Screen.getScreens().firstOrNull { it.bounds.contains(centerX, centerY) }
        } ?: Screen.getPrimary()
        val bounds = screen.visualBounds
        stage.x = bounds.minX + (bounds.width - stage.width) / 2
        stage.y = bounds.minY + (bounds.height - stage.height) / 2
    }

    private fun saveWindowGeometry() {
        val stage = windowStage ?: return
        val settings = widgetsController.currentSettings.imageViewerSettingsModel
        val maximized = windowMaximized

        // While maximized, keep the last normal bounds so un-maximizing the
        // viewer next time restores the size the user actually chose.
        val x = if (maximized) normalWindowX else stage.x
        val y = if (maximized) normalWindowY else stage.y
        val width = if (maximized) normalWindowWidth else stage.width
        val height = if (maximized) normalWindowHeight else stage.height

        var changed = false
        if (settings.windowX != x) {
            settings.windowX = x
            changed = true
        }
        if (settings.windowY != y) {
            settings.windowY = y
            changed = true
        }
        if (settings.windowWidth != width) {
            settings.windowWidth = width
            changed = true
        }
        if (settings.windowHeight != height) {
            settings.windowHeight = height
            changed = true
        }
        if (settings.windowMaximized != maximized) {
            settings.windowMaximized = maximized
            changed = true
        }
        if (changed) {
            WidgetSettings.update(widgetsController.currentSettings)
        }
    }

    private fun saneWindowSize(value: Double, fallback: Double): Double =
        if (value.isFinite() && value >= MIN_WINDOW_SIZE) value else fallback

    override fun onDock() {
        val stage = root.scene?.window as? Stage
        if (stage != null) {
            windowStage = stage
            applySavedWindowGeometry(stage)
            stage.widthProperty().addListener(windowGeometryListener)
            stage.heightProperty().addListener(windowGeometryListener)
            stage.xProperty().addListener(windowGeometryListener)
            stage.yProperty().addListener(windowGeometryListener)
            stage.maximizedProperty().addListener(windowMaximizedListener)
        }
        // Ensure the viewer has focus so keyboard shortcuts work immediately.
        runLater { root.requestFocus() }
    }

    override fun onUndock() {
        sources.firstOrNull()?.let { openViewers.remove(it.postEntityId, this) }
        saveWindowGeometry()
        windowStage?.let { stage ->
            stage.widthProperty().removeListener(windowGeometryListener)
            stage.heightProperty().removeListener(windowGeometryListener)
            stage.xProperty().removeListener(windowGeometryListener)
            stage.yProperty().removeListener(windowGeometryListener)
            stage.maximizedProperty().removeListener(windowMaximizedListener)
        }
        windowStage = null
        resizeDebounce.stop()
        loadingDebounce.stop()
        resetDeepImage()
        navigationJob?.cancel()
        // Invalidate runLater callbacks scheduled by loads that were in flight:
        // the fragment is closing and they must not touch the detached UI.
        latestRequest++
        preloadScope.cancel()
        coroutineScope.cancel()
        decodedCache.clear()
        synchronized(sourceSizes) { sourceSizes.clear() }
        synchronized(encodedInfos) { encodedInfos.clear() }
        imageView.image = null
        thumbCells.forEach { cell ->
            ((cell.children.getOrNull(0) as? StackPane)?.children?.getOrNull(0) as? ImageView)?.image = null
        }
        thumbCells.clear()
        thumbViews.clear()
        thumbBadges.clear()
        thumbTooltips.clear()
        thumbsRequested.clear()
        browseScrollPane = null
        browseTilePane = null
        browseInitialized = false
    }

    companion object {
        // One viewer window per post. Different posts may be open at the same
        // time. Accessed on the JavaFX application thread only.
        private val openViewers = ConcurrentHashMap<Long, PhotoViewerFragment>()

        /**
         * Opens (or re-focuses) the single viewer window for [postEntityId].
         *
         * Must be called on the JavaFX application thread. If a viewer for that
         * post is already showing it is restored from the minimized state,
         * brought to the front and navigated to [initialIndex]; otherwise
         * [create] is invoked to build a fresh viewer, which is then shown
         * non-modally so it can be minimized.
         */
        fun openForPost(postEntityId: Long, initialIndex: Int, create: () -> PhotoViewerFragment) {
            val existing = openViewers[postEntityId]
            if (existing != null) {
                val stage = existing.windowStage
                if (stage != null && stage.isShowing) {
                    if (stage.isIconified) stage.isIconified = false
                    stage.toFront()
                    stage.requestFocus()
                    if (initialIndex in existing.sources.indices) {
                        existing.selectIndex(initialIndex)
                    }
                    return
                }
                // Stale entry (window already gone): drop it and rebuild below.
                openViewers.remove(postEntityId, existing)
            }
            val viewer = create()
            openViewers[postEntityId] = viewer
            viewer.openWindow(owner = null)
        }

        private const val DEFAULT_DECODE_SIZE = 2048.0
        private const val RESIZE_RELOAD_RATIO = 1.05
        private const val MAX_ZOOM = 4.0
        private const val ZOOM_STEP = 1.25

        private const val DEFAULT_WINDOW_WIDTH = 800.0
        private const val DEFAULT_WINDOW_HEIGHT = 600.0
        private const val MIN_WINDOW_SIZE = 100.0

        // How long the viewer stays windowed at startup before a maximized state
        // is re-applied, so the window manager can record the restore geometry.
        private const val INITIAL_MAXIMIZE_DELAY_MS = 10.0

        // No scrollbars: the image is panned by dragging (and via the minimap).
        private const val SCROLLBAR_ALLOWANCE = 0.0

        // Re-decode for deep zoom only once the needed scale is this much larger
        // than the cached image, to avoid a decode per slider step.
        private const val DEEP_ZOOM_RATIO = 1.2

        // Maximum size of the bottom-right minimap, in logical pixels.
        private const val MINIMAP_MAX_WIDTH = 180.0
        private const val MINIMAP_MAX_HEIGHT = 140.0

        // Maximum number of bitmap decodes running concurrently.
        private const val DECODE_CONCURRENCY = 2

        // Largest source-subsampling factor used while decoding (all ImageIO
        // readers used here support at least 1/2/4/8).
        private const val MAX_SUBSAMPLING = 8

        // Browse (thumbnail grid) mode: size of a thumbnail tile, its min/max
        // range (shared with the zoom slider), how many thumbnail downloads run
        // at once, and the tile styling.
        private const val THUMB_SIZE = 200.0
        private const val MIN_THUMB_SIZE = 80.0
        private const val MAX_THUMB_SIZE = 400.0
        private const val THUMB_CELL_PADDING = 8.0
        private const val THUMB_CONCURRENCY = 4
        private const val THUMB_CELL_STYLE =
            "-fx-background-color: rgba(128,128,128,0.08);" +
                    "-fx-background-radius: 6; -fx-border-radius: 6;" +
                    "-fx-border-color: transparent; -fx-border-width: 2; -fx-padding: 6;"
        private const val THUMB_CELL_SELECTED_STYLE =
            "-fx-background-color: rgba(128,128,128,0.18);" +
                    "-fx-background-radius: 6; -fx-border-radius: 6;" +
                    "-fx-border-color: -fx-accent; -fx-border-width: 2; -fx-padding: 6;"
    }
}
