package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.animation.FadeTransition
import javafx.animation.PauseTransition
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.Duration
import kotlinx.coroutines.*
import me.vripper.entities.Status
import me.vripper.gui.controller.ImageController
import me.vripper.gui.controller.PostController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.ImageModel
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.gui.utils.ImageDecodeUtils
import me.vripper.gui.utils.WidgetSettings
import me.vripper.utilities.LoggerDelegate
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.FX
import tornadofx.Fragment
import tornadofx.runLater
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/** Aggregate in-memory cache usage of the open post workspace windows. */
data class WorkspaceCacheStats(
    val windows: Int = 0,
    val decodedFrames: Int = 0,
    val decodedBytes: Long = 0L,
    val decodedLimitBytes: Long = 0L,
    val thumbnailCount: Int = 0,
    val thumbnailBytes: Long = 0L,
)

class PostWorkspaceFragment : Fragment("Image Viewer") {

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sources: List<ImageSource> by param()
    val initialIndex: Int by param()

    // Opened from the post list: start on the overview instead of the view.
    val startInOverview: Boolean by param(false)

    // Used by the refresh action to re-query the current download state.
    private val imageController: ImageController by inject()
    private val postController: PostController by inject()
    private val widgetsController: WidgetsController by inject()
    private val logger by LoggerDelegate()

    private val indexProperty = SimpleIntegerProperty(
        initialIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
    )

    // True while the thumbnail grid is shown; drives the status bar labels.
    private val browseModeProperty = SimpleBooleanProperty(false)

    // Window size/position persistence and restore.
    private val windowGeometry = WindowGeometryController(
        primaryStage = { FX.getPrimaryStage(scope) },
        settings = { widgetsController.currentSettings.workspaceSettingsModel },
        persist = { WidgetSettings.update(widgetsController.currentSettings) }
    )

    // The image display surface (nodes + minimap) is owned by this component;
    // the fields below alias the nodes it builds so the view logic can use them
    // directly.
    private val canvas = ImageCanvasPane()
    private val imageView = canvas.imageView
    private val emptyLabel = canvas.emptyLabel

    // Client-side viewer preferences (memory bounds and preloading).
    private val viewerSettings = WidgetSettings.loadSettings().imageViewerSettings

    // Owns the encoded-byte fetch, decoded-frame cache and speculative preloads.
    private val pipeline = ImageLoadPipeline(
        sources = sources,
        viewerSettings = viewerSettings,
        currentIndex = { indexProperty.get() },
        requestToken = { latestRequest }
    )

    // Deep-zoom state machine for the currently displayed image.
    private val deepZoom: DeepZoomController = DeepZoomController(
        pipeline = pipeline,
        scope = coroutineScope,
        imageView = imageView,
        currentIndex = { indexProperty.get() },
        missingPlaceholder = { missingPlaceholder },
        desiredDisplayScale = { viewZoom.desiredDisplayScale() },
        windowReady = { root.width > 1.0 && root.height > 1.0 },
        hasSources = { sources.isNotEmpty() },
        onDeepImageApplied = { viewZoom.applyZoom() }
    )

    // Zoom/fit/minimap presentation state for the view mode.
    private val viewZoom: ViewZoomController = ViewZoomController(
        canvas = canvas,
        pipeline = pipeline,
        currentIndex = { indexProperty.get() },
        missingPlaceholder = { missingPlaceholder },
        availableSize = {
            val toolbarH = toolbar.layoutBounds.height
            val statusH = statusBar.layoutBounds.height
            (root.width - SCROLLBAR_ALLOWANCE) to
                    (root.height - toolbarH - statusH - SCROLLBAR_ALLOWANCE)
        },
        outputScale = { root.scene?.window?.outputScaleX?.takeIf { it > 0.0 } ?: 1.0 },
        maxZoom = MAX_ZOOM,
        onZoomControls = { z, f ->
            // While browsing, the slider drives the thumbnail size; an async
            // image decode must not push the view zoom back into it.
            if (!browseMode) {
                updatingZoomControls = true
                zoomSlider.min = f * 100.0
                zoomSlider.max = MAX_ZOOM * 100.0
                zoomSlider.value = z * 100.0
                updatingZoomControls = false
            }
        },
        onTitleRefresh = { refreshTitle() },
        onZoomApplied = { immediate ->
            deepZoom.update(immediate)
            canvas.refreshMinimapViewport()
            refreshStatusBar()
        }
    )

    private var toolbar: ToolBar
    private val scrollPane = canvas.scrollPane
    private val centerStack = canvas.centerStack
    private var zoomSlider: Slider
    private var zoomOutButton: Button
    private var zoomInButton: Button
    private var fitButton: Button
    private var fillButton: Button
    private var statusLabel: Label
    private var indexLabel: Label
    private var sourceLabel: Label
    private var statusBar: VBox
    private var progressBar: ProgressBar

    // Overview mode: a read-only details panel plus a virtualized grid or a
    // table of every image. Double-clicking an entry switches back to view mode
    // focused on that image. Built lazily on first use.
    private var viewToggle: ToggleButton
    private var browseToggle: ToggleButton
    private var workspacePane: PostWorkspacePane? = null
    private var browseMode = false

    // Guards the zoom slider against feedback loops while it is updated
    // programmatically.
    private var updatingZoomControls = false

    // Sentinel for "no image available"; the single instance is owned by the
    // pipeline so identity comparisons stay valid.
    private val missingPlaceholder: Image get() = pipeline.missingPlaceholder

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
        center = canvas.centerStack
        BorderPane.setAlignment(canvas.centerStack, Pos.CENTER)

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
        browseToggle = ToggleButton("Overview").apply {
            graphic = FontIcon.of(Feather.GRID)
            toggleGroup = modeGroup
            tooltip = Tooltip("Overview: details and all images")
            setOnAction {
                if (!isSelected) {
                    isSelected = true
                } else {
                    setMode(browse = true)
                }
            }
        }

        val startButton = Button().apply {
            graphic = FontIcon.of(Feather.PLAY)
            styleClass.add(Styles.BUTTON_ICON)
            tooltip = Tooltip("Start downloads for this post")
            visibleProperty().bind(browseModeProperty)
            managedProperty().bind(browseModeProperty)
            setOnAction { startPostDownloads() }
        }

        val stopButton = Button().apply {
            graphic = FontIcon.of(Feather.SQUARE)
            styleClass.add(Styles.BUTTON_ICON)
            tooltip = Tooltip("Stop downloads for this post")
            visibleProperty().bind(browseModeProperty)
            managedProperty().bind(browseModeProperty)
            setOnAction { stopPostDownloads() }
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

        zoomOutButton = Button().apply {
            graphic = FontIcon.of(Feather.ZOOM_OUT)
            styleClass.add(Styles.BUTTON_ICON)
            setOnAction { zoomOut() }
        }

        zoomSlider = Slider(viewZoom.fitZoom * 100.0, MAX_ZOOM * 100.0, viewZoom.zoom * 100.0).apply {
            prefWidth = 160.0
            blockIncrement = 10.0
        }

        zoomInButton = Button().apply {
            graphic = FontIcon.of(Feather.ZOOM_IN)
            styleClass.add(Styles.BUTTON_ICON)
            setOnAction { zoomIn() }
        }

        fitButton = Button("Fit").apply {
            tooltip = Tooltip("Fit to window")
            setOnAction { viewZoom.setZoom(viewZoom.fitZoom) }
        }

        fillButton = Button("100%").apply {
            setOnAction { viewZoom.setZoom(1.0) }
        }

        toolbar = ToolBar(
            browseToggle,
            viewToggle,
            Separator(Orientation.VERTICAL),
            startButton,
            stopButton,
            Separator(Orientation.VERTICAL),
            prevButton,
            nextButton,
            Separator(Orientation.VERTICAL),
            fitButton,
            zoomOutButton,
            zoomSlider,
            zoomInButton,
            fillButton
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
                viewZoom.setZoom(value.toDouble() / 100.0)
            }
        }

        fun scheduleResize() {
            runLater {
                updateDecodeTarget()
                viewZoom.recomputeFit(resetToFit = false)
            }
        }

        // Re-evaluate whenever container size or toolbar size changes
        layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }
        toolbar.layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }
        statusBar.layoutBoundsProperty().addListener { _, _, _ -> scheduleResize() }

        // Re-evaluate whenever the image changes (so it works on prev/next)
        imageView.imageProperty().addListener { _, _, _ -> scheduleResize() }

        // Keep the minimap viewport box in sync while panning/zooming.
        scrollPane.hvalueProperty().addListener { _, _, _ -> viewZoom.refreshMinimapViewport() }
        scrollPane.vvalueProperty().addListener { _, _, _ -> viewZoom.refreshMinimapViewport() }
        scrollPane.viewportBoundsProperty().addListener { _, _, _ -> viewZoom.refreshMinimapViewport() }
        imageView.boundsInParentProperty().addListener { _, _, _ -> viewZoom.refreshMinimapViewport() }

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
                    if (browseMode) setThumbSize(PostWorkspacePane.THUMB_SIZE) else viewZoom.setZoom(viewZoom.fitZoom)
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
        // Let in-flight downloads finish instead of resetting their gRPC
        // streams; speculative decodes check this flag and are skipped.
        pipeline.suspendPreloads()
    }

    private fun resumePreloads() {
        pipeline.resumePreloads(indexProperty.get())
    }

    init {
        // The scene has not been laid out yet. Start from the default decode
        // size rather than the full display, which would keep every image
        // decoded at (e.g.) 4K even in a small window. The first layout pass
        // then raises the target and triggers a rebuild if the window is larger.
        pipeline.markBuiltForDefault(ImageLoadPipeline.DEFAULT_DECODE_SIZE)
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
                val image = pipeline.loadImageFor(startIndex)
                runLater {
                    if (request != latestRequest) return@runLater
                    showImage(startIndex, image)
                    pipeline.preloadAround(startIndex)
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
        if (startInOverview) {
            setMode(browse = true)
        }
    }

    private fun navigate(delta: Int) {
        if (sources.isEmpty()) return
        if (browseMode) {
            // While browsing, next/previous move the highlighted thumbnail
            // instead of loading an image.
            workspacePane?.selectRelative(delta)
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
            workspacePane?.selectSourceIndex(index)
        } else {
            navigateTo(index)
        }
    }

    private fun navigateTo(newIndex: Int) {
        if (sources.isEmpty()) return
        if (newIndex == indexProperty.get()) return
        navigationTarget = newIndex
        deepZoom.reset()
        startLoading()
        val request = ++latestRequest
        // Cancel any in-flight navigation so a slow decode cannot overwrite a
        // newer selection (and so we do not hold several large bitmaps at once).
        navigationJob?.cancel()
        // Do not cancel the preload scope here: in-flight preloads are already
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
        // deep zoom still decodes a sharper copy if the user is zoomed in.
        val atFit = viewZoom.isAtFit()
        val minScale = if (atFit) 0.0 else viewZoom.desiredDisplayScale()
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
            val image = pipeline.loadImageFor(newIndex, minScale = minScale)
            val model = modelDeferred?.await()
            if (!isActive) return@launch
            runLater {
                if (request != latestRequest) return@runLater
                if (model != null) applyModelState(model, source)
                showImage(newIndex, image, resetZoom = false, immediateDeepZoom = true)
            }
            pipeline.preloadAround(newIndex)
        }
    }

    /**
     * Opens [index] in view mode after selecting it in browse mode. The
     * previously displayed image is cleared immediately so it cannot linger
     * while the selected one is decoded and loaded.
     */
    private fun chooseFromBrowse(index: Int) {
        workspacePane?.let {
            it.browseIndex = index
        }
        setMode(browse = false)
        // The window may have been opened before this image finished
        // downloading; switching to the same index would otherwise keep the
        // "not downloaded yet" placeholder forever.
        if (index == indexProperty.get() && imageView.image === missingPlaceholder) {
            reloadCurrent()
        }
    }

    /**
     * Shows the overview and highlights [index], switching modes if the window
     * is currently displaying a single image.
     */
    private fun showOverview(index: Int) {
        if (sources.isEmpty()) return
        setMode(browse = true)
        configureZoomControls(browse = true)
        workspacePane?.selectSourceIndex(index.coerceIn(0, sources.lastIndex))
    }

    /**
     * Shows the single-image view at [index], leaving the overview first when
     * that is the mode currently displayed.
     */
    private fun showViewerAt(index: Int) {
        if (sources.isEmpty()) return
        val target = index.coerceIn(0, sources.lastIndex)
        if (browseMode) {
            // setMode(false) navigates to the browse selection itself.
            workspacePane?.browseIndex = target
            setMode(browse = false)
        } else if (target != indexProperty.get()) {
            navigateTo(target)
        }
        if (target == indexProperty.get() && imageView.image === missingPlaceholder) {
            reloadCurrent()
        }
    }

    /**
     * Switches between the single-image view mode and the workspace overview
     * (browse) mode.
     */
    private fun setMode(browse: Boolean) {
        if (browseMode == browse) return
        browseMode = browse
        browseModeProperty.set(browse)
        viewToggle.isSelected = !browse
        browseToggle.isSelected = browse
        if (browse) {
            val pane = workspacePane ?: PostWorkspacePane(
                sources = sources,
                scope = coroutineScope,
                widgetsController = widgetsController,
                imageController = imageController,
                postController = postController,
                stage = currentStage!!,
                onActivate = { sourceIndex -> chooseFromBrowse(sourceIndex) },
                onLayoutChanged = {
                    if (browseMode) configureZoomControls(browse = true)
                },
                onThumbSizeChanged = { size ->
                    updatingZoomControls = true
                    zoomSlider.value = size
                    updatingZoomControls = false
                },
                onOverviewTitleChanged = { overviewTitle ->
                    if (browseMode && title != overviewTitle) title = overviewTitle
                }
            ).also { workspacePane = it }
            pane.selectSourceIndex(indexProperty.get().coerceIn(0, max(0, sources.size - 1)))
            viewZoom.hideMinimap()
            root.center = pane.root
            configureZoomControls(browse = true)
            title = pane.overviewTitle()
        } else {
            val target = workspacePane?.browseIndex ?: indexProperty.get()
            root.center = centerStack
            configureZoomControls(browse = false)
            if (target in sources.indices && target != indexProperty.get()) {
                imageView.image = null
                emptyLabel.isVisible = false
                navigateTo(target)
                // The new image is still loading; show its name right away.
                title = sources[target].fileName().ifBlank { "Image Viewer" }
            } else if (pipeline.targetOutgrewCache()) {
                // The window was enlarged while browsing and the rebuild was
                // deferred; re-decode now that an image is displayed again.
                rebuildCacheForCurrentSize()
                refreshTitle()
            } else {
                viewZoom.refreshMinimapSize()
                refreshTitle()
            }
        }
    }

    /**
     * Points the zoom controls at the current mode: the image zoom while
     * viewing, or the thumbnail size while browsing the grid. Fit/fill have no
     * meaning on the grid, and the thumbnail zoom has no meaning on the table
     * layout, so the irrelevant controls are hidden.
     */
    private fun configureZoomControls(browse: Boolean) {
        updatingZoomControls = true
        val gridLayout = browse && workspacePane?.layout != "TABLE"
        zoomSlider.isVisible = !browse || gridLayout
        zoomSlider.isManaged = zoomSlider.isVisible
        zoomOutButton.isVisible = !browse || gridLayout
        zoomOutButton.isManaged = zoomOutButton.isVisible
        zoomInButton.isVisible = !browse || gridLayout
        zoomInButton.isManaged = zoomInButton.isVisible
        if (browse) {
            zoomSlider.min = PostWorkspacePane.MIN_THUMB_SIZE
            zoomSlider.max = PostWorkspacePane.MAX_THUMB_SIZE
            zoomSlider.value = workspacePane?.thumbSize ?: PostWorkspacePane.THUMB_SIZE
            fitButton.isVisible = false
            fitButton.isManaged = false
            fillButton.isVisible = false
            fillButton.isManaged = false
        } else {
            zoomSlider.min = viewZoom.fitZoom * 100.0
            zoomSlider.max = MAX_ZOOM * 100.0
            zoomSlider.value = viewZoom.zoom * 100.0
            fitButton.isVisible = true
            fitButton.isManaged = true
            fillButton.isVisible = true
            fillButton.isManaged = true
        }
        updatingZoomControls = false
    }

    private fun zoomIn() {
        if (browseMode) {
            val pane = workspacePane ?: return
            pane.setThumbSize(pane.thumbSize * ZOOM_STEP)
        } else {
            viewZoom.zoomIn(ZOOM_STEP)
        }
    }

    private fun zoomOut() {
        if (browseMode) {
            val pane = workspacePane ?: return
            pane.setThumbSize(pane.thumbSize / ZOOM_STEP)
        } else {
            viewZoom.zoomOut(ZOOM_STEP)
        }
    }

    /**
     * Resizes the thumbnail tiles. Bounded so the grid stays usable, and kept
     * in sync with the zoom slider through the workspace pane callback.
     */
    private fun setThumbSize(newSize: Double) {
        workspacePane?.setThumbSize(newSize)
    }

    private fun startPostDownloads() {
        val postId = sources.firstOrNull()?.postEntityId?.takeIf { it >= 0 } ?: return
        coroutineScope.launch {
            postController.start(listOf(postId))
        }
    }

    private fun stopPostDownloads() {
        val postId = sources.firstOrNull()?.postEntityId?.takeIf { it >= 0 } ?: return
        coroutineScope.launch {
            postController.stop(listOf(postId))
        }
    }

    /**
     * Re-reads the current images from the backend, keyed by image id. Used when
     * the viewer needs file names / download state that changed meanwhile.
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
        pipeline.cancelPreloads()
        val minScale = viewZoom.desiredDisplayScale()
        navigationJob = coroutineScope.launch {
            val image = pipeline.loadImageFor(index, minScale = minScale)
            if (!isActive) return@launch
            runLater {
                if (request != latestRequest) return@runLater
                showImage(index, image, animate = false, resetZoom = false)
            }
            pipeline.preloadAround(index)
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
        pipeline.pin(index)
        val displayed = deepZoom.displayedImage(index, image)
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
        viewZoom.refreshMinimapSize()
        // Title and zoom are refreshed once the display size is known.
        viewZoom.recomputeFit(resetToFit = resetZoom, immediateDeepZoom = immediateDeepZoom)

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
     * Sets the window title from the currently displayed image: file name, its
     * real resolution and the zoom percentage. Reading the source resolution
     * was already done on a background thread while loading, so this is cheap
     * and always matches the image currently on screen.
     */
    private fun refreshTitle() {
        // While browsing, the title describes the post, not the last image.
        if (browseMode) return
        val index = indexProperty.get()
        val fileName = sources.getOrNull(index)?.fileName().orEmpty()
        if (imageView.image === missingPlaceholder) {
            title = fileName.ifBlank { "Image Viewer" }
            return
        }
        val size = viewZoom.currentSourceSize()
        if (size == null || fileName.isBlank()) {
            title = fileName.ifBlank { "Image Viewer" }
            return
        }
        val (sourceWidth, sourceHeight) = size
        val percent = (viewZoom.zoom * 100.0).roundToInt()
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
        val size = viewZoom.currentSourceSize()
        val dimensions = if (size != null && size.first > 0 && size.second > 0) {
            "${size.first} × ${size.second}"
        } else {
            "?"
        }
        val info = pipeline.encodedInfo(index)
        val format = info?.format ?: "?"
        val byteSize = info?.byteSize?.let { ImageDecodeUtils.formatSize(it) } ?: "?"
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
        // after it is enlarged again. If the window grew, the cached images may
        // now be too small: re-decode them once the resize settles.
        if (pipeline.growDecodeTarget(targetWidth, targetHeight)) {
            resizeDebounce.playFromStart()
        }
    }

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
        if (pipeline.decodeWidth <= 0.0 || pipeline.decodeHeight <= 0.0) return
        // A navigation may still be loading (indexProperty lags until the image
        // is shown): rebuild its target, not the image still on screen.
        val current = if (navigationTarget in sources.indices) navigationTarget else indexProperty.get()
        navigationTarget = current
        pipeline.markBuiltForCurrentTarget()
        pipeline.clearCache()

        val request = ++latestRequest
        // Read the display scale on the FX thread, like the other load paths.
        val minScale = viewZoom.desiredDisplayScale()
        navigationJob?.cancel()
        pipeline.cancelPreloads()
        navigationJob = coroutineScope.launch {
            val source = sources[current]
            // A rebuild may replace an in-flight navigation, so pick up the
            // file name / download state it would have refreshed.
            val modelDeferred = if (source.fileName().isBlank()) {
                async { modelsById()[source.imageId] }
            } else {
                null
            }
            val image = pipeline.loadImageFor(current, minScale = minScale)
            val model = modelDeferred?.await()
            if (!isActive) return@launch
            runLater {
                if (request != latestRequest) return@runLater
                if (model != null) applyModelState(model, source)
                showImage(current, image, animate = false, resetZoom = false)
            }
            pipeline.preloadAround(current)
        }
    }

    private fun isWithin(target: Any?, ancestor: Node): Boolean {
        var node = target as? Node
        while (node != null) {
            if (node === ancestor) return true
            node = node.parent
        }
        return false
    }

    override fun onDock() {
        val stage = root.scene?.window as? Stage
        if (stage != null) {
            windowGeometry.attach(stage)
        }
        // Ensure the viewer has focus so keyboard shortcuts work immediately.
        runLater { root.requestFocus() }
    }

    override fun onUndock() {
        sources.firstOrNull()?.let { openViewers.remove(it.postEntityId, this) }
        windowGeometry.detach()
        resizeDebounce.stop()
        loadingDebounce.stop()
        deepZoom.reset()
        navigationJob?.cancel()
        // Invalidate runLater callbacks scheduled by loads that were in flight:
        // the fragment is closing and they must not touch the detached UI.
        latestRequest++
        pipeline.dispose()
        coroutineScope.cancel()
        imageView.image = null
        workspacePane?.dispose()
        workspacePane = null
    }

    /** In-memory cache usage of this window; called on the FX thread. */
    private fun localCacheStats(): WorkspaceCacheStats {
        val decoded = pipeline.decodedCacheStats()
        val thumbs = workspacePane?.thumbnailStats()
        return WorkspaceCacheStats(
            windows = 1,
            decodedFrames = decoded.frames,
            decodedBytes = decoded.bytes,
            decodedLimitBytes = decoded.maxBytes,
            thumbnailCount = thumbs?.count ?: 0,
            thumbnailBytes = thumbs?.bytes ?: 0L,
        )
    }

    companion object {
        // One viewer window per post. Different posts may be open at the same
        // time. Accessed on the JavaFX application thread only.
        private val openViewers = ConcurrentHashMap<Long, PostWorkspaceFragment>()

        /**
         * Opens (or re-focuses) the single viewer window for [postEntityId].
         *
         * Must be called on the JavaFX application thread. If a viewer for that
         * post is already showing it is restored from the minimized state,
         * brought to the front and navigated to [initialIndex]; otherwise
         * [create] is invoked to build a fresh viewer, which is then shown
         * non-modally so it can be minimized.
         */
        fun openForPost(
            postEntityId: Long,
            initialIndex: Int,
            startInOverview: Boolean = false,
            create: () -> PostWorkspaceFragment
        ) {
            val existing = openViewers[postEntityId]
            if (existing != null) {
                val stage = existing.windowGeometry.stage
                if (stage != null && stage.isShowing) {
                    if (stage.isIconified) stage.isIconified = false
                    stage.toFront()
                    stage.requestFocus()
                    if (startInOverview) {
                        existing.showOverview(initialIndex)
                    } else {
                        existing.showViewerAt(initialIndex)
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

        /**
         * Closes every open workspace window. Workspace windows are unowned so
         * they can be minimized independently, which means they would otherwise
         * survive the main window and keep the application alive.
         */
        fun closeAll() {
            openViewers.values.toList().forEach { it.close() }
            openViewers.clear()
        }

        /**
         * Aggregates the in-memory caches of every open workspace window.
         * Must be called on the JavaFX application thread.
         */
        fun cacheStats(): WorkspaceCacheStats {
            var windows = 0
            var decodedFrames = 0
            var decodedBytes = 0L
            var decodedLimitBytes = 0L
            var thumbnailCount = 0
            var thumbnailBytes = 0L
            openViewers.values.forEach { viewer ->
                val stats = viewer.localCacheStats()
                windows++
                decodedFrames += stats.decodedFrames
                decodedBytes += stats.decodedBytes
                decodedLimitBytes += stats.decodedLimitBytes
                thumbnailCount += stats.thumbnailCount
                thumbnailBytes += stats.thumbnailBytes
            }
            return WorkspaceCacheStats(
                windows = windows,
                decodedFrames = decodedFrames,
                decodedBytes = decodedBytes,
                decodedLimitBytes = decodedLimitBytes,
                thumbnailCount = thumbnailCount,
                thumbnailBytes = thumbnailBytes,
            )
        }

        private const val MAX_ZOOM = 4.0
        private const val ZOOM_STEP = 1.25

        // No scrollbars: the image is panned by dragging (and via the minimap).
        private const val SCROLLBAR_ALLOWANCE = 0.0
    }
}
