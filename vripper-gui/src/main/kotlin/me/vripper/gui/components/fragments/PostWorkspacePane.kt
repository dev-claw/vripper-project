package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.animation.AnimationTimer
import javafx.animation.PauseTransition
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.text.TextAlignment
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import me.vripper.entities.Status
import me.vripper.gui.components.cells.ProgressTableCell
import me.vripper.gui.components.cells.StatusTableCell
import me.vripper.gui.controller.ImageController
import me.vripper.gui.controller.PostController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.ImageModel
import me.vripper.gui.utils.ImageCacheManager
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.addClass
import tornadofx.onChange
import tornadofx.onDoubleClick
import tornadofx.runLater
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The overview workspace: a read-only details panel beside a virtualized
 * thumbnail grid or a table of every image in the post. Owns the live post
 * state, the thumbnail cache and the per-workspace layout/column settings.
 *
 * The owning window keeps the mode toggle and the view-mode zoom controls; this
 * pane reports selection/layout/thumbnail-size changes through callbacks.
 */
internal class PostWorkspacePane(
    private val sources: List<ImageSource>,
    private val scope: CoroutineScope,
    private val widgetsController: WidgetsController,
    private val imageController: ImageController,
    private val postController: PostController,
    private val onActivate: (sourceIndex: Int) -> Unit,
    private val onLayoutChanged: () -> Unit,
    private val onThumbSizeChanged: (Double) -> Unit
) {

    val root = BorderPane()

    /** Highlighted source index, mirrored from the grid/table selection. */
    var browseIndex = 0

    var thumbSize = THUMB_SIZE
        private set

    var layout = "GRID"
        private set

    private val listPane = StackPane()
    private var scroll: ScrollPane? = null
    private var grid: VirtualGridView? = null
    private var table: TableView<ImageModel>? = null
    private var details: PostDetailsPane? = null
    private var split: SplitPane? = null
    private var gridToggle: ToggleButton? = null
    private var tableToggle: ToggleButton? = null
    private var workspaceState: PostWorkspaceState? = null
    private val thumbImages = HashMap<Long, Image>()
    private val thumbsRequested = HashSet<Long>()
    private val gridCells = mutableSetOf<Region>()
    private var shadeAnimator: AnimationTimer? = null
    private var lastShadePulseNanos = 0L

    // Debounce so dragging a table column does not persist on every pixel.
    private val columnWidthDebounce = PauseTransition(Duration.millis(300.0)).apply {
        setOnFinished { persistColumnWidths() }
    }

    init {
        val settings = widgetsController.currentSettings.workspaceSettingsModel
        layout = if (settings.browseLayout == "TABLE") "TABLE" else "GRID"

        val postId = sources.firstOrNull()?.postEntityId ?: -1L
        if (postId >= 0) {
            workspaceState = PostWorkspaceState(postId, imageController, postController)
        }
        val images = workspaceState?.images ?: javafx.collections.FXCollections.observableArrayList()

        val tile = VirtualGridView { workspaceState?.images?.size ?: 0 }.apply {
            cellWidth = thumbSize + THUMB_CELL_PADDING * 2.0
            cellHeight = cellWidth + THUMB_CAPTION_HEIGHT
            hgap = 8.0
            vgap = 8.0
            cellStyle = THUMB_CELL_STYLE
            selectedCellStyle = THUMB_CELL_SELECTED_STYLE
            cellFactory = { buildGridCell() }
            cellBinder = { cell, index -> bindGridCell(cell, index) }
            onSelect = { index -> selectOverviewIndex(index) }
            onActivate = { index -> activateOverviewIndex(index) }
        }
        val scrollPane = ScrollPane(tile).apply {
            isFitToWidth = true
            isFitToHeight = false
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
        tile.attach(scrollPane)
        grid = tile
        scroll = scrollPane

        table = buildTable(images)
        details = PostDetailsPane()
        split = SplitPane().apply {
            orientation = Orientation.HORIZONTAL
            items.add(listPane)
            items.add(details!!.root)
            getDividers().getOrNull(0)?.positionProperty()?.addListener { _, _, value ->
                widgetsController.currentSettings.workspaceSettingsModel.detailsPanelDividerPosition =
                    value.toDouble()
            }
        }
        applyLayout()
        updateDetailsPanel()

        root.center = split
        root.top = buildHeader()

        workspaceState?.let { state ->
            state.postModel.addListener { _, _, model -> details?.setPostModel(model) }
            state.images.addListener(ListChangeListener {
                runLater {
                    ensureThumbs()
                    grid?.refreshAll()
                    table?.refresh()
                    highlightTile()
                }
            })
            state.start()
        }
        ensureThumbs()
    }

    /**
     * Selects [sourceIndex] (highlight + scroll into view) without activating it.
     */
    fun selectSourceIndex(sourceIndex: Int) {
        if (sources.isEmpty()) return
        browseIndex = sourceIndex.coerceIn(0, sources.lastIndex)
        highlightTile()
        scrollTileIntoView()
    }

    /** Moves the selection by [delta], wrapping around the post. */
    fun selectRelative(delta: Int) {
        if (sources.isEmpty()) return
        browseIndex = ((browseIndex + delta) % sources.size + sources.size) % sources.size
        highlightTile()
        scrollTileIntoView()
    }

    fun setThumbSize(newSize: Double) {
        thumbSize = newSize.coerceIn(MIN_THUMB_SIZE, MAX_THUMB_SIZE)
        onThumbSizeChanged(thumbSize)
        applyThumbSize()
    }

    /** Releases the resources held by the overview pane. */
    fun dispose() {
        columnWidthDebounce.stop()
        persistColumnWidths()
        workspaceState?.stop()
        workspaceState = null
        gridCells.forEach(::detachProgressAnimation)
        stopShadeAnimator()
        gridCells.clear()
        grid?.dispose()
        grid = null
        table = null
        scroll = null
        details = null
        split = null
        thumbImages.clear()
        thumbsRequested.clear()
    }

    private fun buildHeader(): Region {
        val group = ToggleGroup()
        gridToggle = ToggleButton("Grid").apply {
            graphic = FontIcon.of(Feather.GRID)
            toggleGroup = group
            setOnAction { setLayout("GRID") }
        }
        tableToggle = ToggleButton("Table").apply {
            graphic = FontIcon.of(Feather.LIST)
            toggleGroup = group
            setOnAction { setLayout("TABLE") }
        }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val detailsToggle = ToggleButton("Details").apply {
            isSelected = widgetsController.currentSettings.workspaceSettingsModel.detailsPanelVisible
            setOnAction {
                widgetsController.currentSettings.workspaceSettingsModel.detailsPanelVisible = isSelected
                updateDetailsPanel()
            }
        }
        return HBox(6.0, gridToggle, tableToggle, spacer, detailsToggle).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(6.0, 8.0, 6.0, 8.0)
            style = "-fx-border-color: rgba(128,128,128,0.25); -fx-border-width: 0 0 1 0;"
        }
    }

    private fun setLayout(newLayout: String) {
        if (layout == newLayout) return
        layout = newLayout
        widgetsController.currentSettings.workspaceSettingsModel.browseLayout = newLayout
        applyLayout()
        onLayoutChanged()
    }

    private fun applyLayout() {
        val showGrid = layout != "TABLE"
        gridToggle?.isSelected = showGrid
        tableToggle?.isSelected = !showGrid
        listPane.children.setAll(if (showGrid) scroll else table)
    }

    private fun updateDetailsPanel() {
        val pane = split ?: return
        val panel = details?.root ?: return
        val visible = widgetsController.currentSettings.workspaceSettingsModel.detailsPanelVisible
        val items = pane.items
        if (visible) {
            if (!items.contains(panel)) items.add(panel)
            pane.setDividerPositions(
                widgetsController.currentSettings.workspaceSettingsModel.detailsPanelDividerPosition
            )
        } else {
            items.remove(panel)
        }
    }

    /**
     * Table layout of the overview. Uses its own column model/widths (separate
     * from the main images tab) and is virtualized by [TableView].
     */
    private fun buildTable(images: ObservableList<ImageModel>): TableView<ImageModel> {
        val columns = widgetsController.currentSettings.workspaceImagesColumnsModel
        val widths = widgetsController.currentSettings.workspaceImagesColumnsWidthModel
        val view = TableView(images)
        view.isTableMenuButtonVisible = true
        view.addClass(Styles.DENSE, Tweaks.EDGE_TO_EDGE)
        view.maxWidth = Double.MAX_VALUE
        view.maxHeight = Double.MAX_VALUE
        view.setRowFactory {
            val row = TableRow<ImageModel>()
            row.onDoubleClick { row.item?.let { activateModel(it) } }
            row
        }
        view.columns.add(
            TableColumn<ImageModel, String>("Preview").apply {
                isVisible = columns.preview
                visibleProperty().onChange { columns.preview = it }
                prefWidth = widths.preview
                widthProperty().onChange { columnWidthDebounce.playFromStart() }
                setCellValueFactory { it.value.thumbUrlProperty }
                setCellFactory {
                    object : TableCell<ImageModel, String>() {
                        private val cellView = ImageView().apply {
                            isPreserveRatio = true
                            isSmooth = true
                            isCache = true
                            fitWidth = 64.0
                            fitHeight = 64.0
                        }

                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            val model = tableRow?.item
                            graphic = if (empty || model == null) null else cellView
                            cellView.image = model?.let { thumbImages[it.id] }
                        }
                    }
                }
            }
        )
        view.columns.add(textColumn("Index", columns.index, widths.index, { it.indexProperty }) { columns.index = it })
        view.columns.add(textColumn("Link", columns.link, widths.link, { it.urlProperty }) { columns.link = it })
        view.columns.add(
            TableColumn<ImageModel, Number>("Progress").apply {
                isVisible = columns.progress
                visibleProperty().onChange { columns.progress = it }
                prefWidth = widths.progress
                widthProperty().onChange { columnWidthDebounce.playFromStart() }
                setCellValueFactory { it.value.progressProperty }
                setCellFactory {
                    ProgressTableCell<ImageModel>().apply {
                        alignment = Pos.CENTER
                    } as TableCell<ImageModel, Number>
                }
            }
        )
        view.columns.add(
            textColumn(
                "Filename",
                columns.filename,
                widths.filename,
                { it.filenameProperty }) { columns.filename = it })
        view.columns.add(
            TableColumn<ImageModel, String>("Status").apply {
                isVisible = columns.status
                visibleProperty().onChange { columns.status = it }
                prefWidth = widths.status
                widthProperty().onChange { columnWidthDebounce.playFromStart() }
                setCellValueFactory { it.value.statusProperty }
                setCellFactory { StatusTableCell() }
            }
        )
        view.columns.add(textColumn("Size", columns.size, widths.size, { it.sizeProperty }) { columns.size = it })
        view.columns.add(
            textColumn("Downloaded", columns.downloaded, widths.downloaded, { it.downloadedProperty }) {
                columns.downloaded = it
            }
        )
        view.selectionModel.selectedIndexProperty().onChange { index ->
            if (index != null && index >= 0) selectOverviewIndex(index)
        }
        return view
    }

    private fun textColumn(
        title: String,
        visible: Boolean,
        width: Double,
        value: (ImageModel) -> ObservableValue<*>,
        onVisible: (Boolean) -> Unit,
    ): TableColumn<ImageModel, Any> {
        return TableColumn<ImageModel, Any>(title).apply {
            isVisible = visible
            prefWidth = width
            visibleProperty().onChange { onVisible(it) }
            widthProperty().onChange { columnWidthDebounce.playFromStart() }
            setCellValueFactory { data ->
                @Suppress("UNCHECKED_CAST")
                value(data.value) as ObservableValue<Any>
            }
            setCellFactory {
                TextFieldTableCell<ImageModel, Any>().apply { alignment = Pos.CENTER_LEFT }
            }
        }
    }

    private fun persistColumnWidths() {
        val current = table ?: return
        val widths = widgetsController.currentSettings.workspaceImagesColumnsWidthModel
        val cols = current.columns
        cols.getOrNull(0)?.let { widths.preview = it.width }
        cols.getOrNull(1)?.let { widths.index = it.width }
        cols.getOrNull(2)?.let { widths.link = it.width }
        cols.getOrNull(3)?.let { widths.progress = it.width }
        cols.getOrNull(4)?.let { widths.filename = it.width }
        cols.getOrNull(5)?.let { widths.status = it.width }
        cols.getOrNull(6)?.let { widths.size = it.width }
        cols.getOrNull(7)?.let { widths.downloaded = it.width }
    }

    private fun buildGridCell(): Region {
        val thumbView = ImageView().apply {
            isPreserveRatio = true
            isSmooth = true
            isCache = true
            fitWidth = thumbSize
            fitHeight = thumbSize
        }
        val coverage = SimpleDoubleProperty(1.0)
        // Covers the part of the image that is not available yet. It is anchored
        // to the right edge and shrinks as the download progresses, revealing
        // the image from left to right. Hidden near zero so a finished image
        // cannot leave a 1px sliver behind.
        val shade = Region().apply {
            style = "-fx-background-color: rgba(70, 70, 70, 0.60);"
            isMouseTransparent = true
            visibleProperty().bind(coverage.greaterThan(0.01))
            minHeightProperty().bind(thumbView.fitHeightProperty())
            prefHeightProperty().bind(thumbView.fitHeightProperty())
            maxHeightProperty().bind(thumbView.fitHeightProperty())
        }
        val box = StackPane(thumbView, shade).apply {
            alignment = Pos.CENTER
            // Fixed to the tile size so the shade's edges stay on the tile
            // regardless of the image aspect ratio or whether the thumb has
            // loaded yet.
            minWidth = thumbSize
            prefWidth = thumbSize
            maxWidth = thumbSize
            minHeight = thumbSize
            prefHeight = thumbSize
            maxHeight = thumbSize
        }
        StackPane.setAlignment(shade, Pos.CENTER_RIGHT)
        val title = Label().apply {
            alignment = Pos.CENTER
            textAlignment = TextAlignment.CENTER
            maxWidth = Double.MAX_VALUE
            style = "-fx-font-size: 11px;"
        }
        val details = Label().apply {
            alignment = Pos.CENTER
            textAlignment = TextAlignment.CENTER
            maxWidth = Double.MAX_VALUE
            style = "-fx-font-size: 10px; -fx-opacity: 0.70;"
        }
        val caption = VBox(2.0, title, details).apply {
            alignment = Pos.CENTER
            maxWidth = Double.MAX_VALUE
        }
        val tooltip = Tooltip()
        val cell = VBox(box, caption).apply {
            alignment = Pos.CENTER
            spacing = 4.0
            isPickOnBounds = true
            style = THUMB_CELL_STYLE
            Tooltip.install(this, tooltip)
            properties["thumbView"] = thumbView
            properties["box"] = box
            properties["shade"] = shade
            properties["title"] = title
            properties["details"] = details
            properties["tooltip"] = tooltip
            properties["shadeCoverage"] = coverage
            properties["shadeTarget"] = SimpleDoubleProperty(1.0)
        }
        gridCells.add(cell)
        return cell
    }

    private fun bindGridCell(cell: Region, index: Int) {
        val model = workspaceState?.images?.getOrNull(index) ?: return
        val thumbView = cell.properties["thumbView"] as ImageView
        if (cell.properties["boundModel"] === model && cell.properties["boundThumbSize"] == thumbSize) {
            // The grid re-binds its visible cells on every layout pass, which
            // happens on each animation pulse. Leave the running transition
            // untouched and only refresh the (possibly late) thumbnail.
            thumbView.image = thumbImages[model.id]
            return
        }
        detachProgressAnimation(cell)
        val box = cell.properties["box"] as StackPane
        val shade = cell.properties["shade"] as Region
        val title = cell.properties["title"] as Label
        val details = cell.properties["details"] as Label
        val tooltip = cell.properties["tooltip"] as Tooltip
        val coverage = cell.properties["shadeCoverage"] as SimpleDoubleProperty
        val target = cell.properties["shadeTarget"] as SimpleDoubleProperty
        thumbView.fitWidth = thumbSize
        thumbView.fitHeight = thumbSize
        box.minWidth = thumbSize
        box.prefWidth = thumbSize
        box.maxWidth = thumbSize
        box.minHeight = thumbSize
        box.prefHeight = thumbSize
        box.maxHeight = thumbSize
        title.textProperty().unbind()
        title.textProperty().bind(
            Bindings.createStringBinding(
                { model.filename.ifBlank { "Image ${model.index}" } },
                model.filenameProperty,
                model.indexProperty
            )
        )
        details.textProperty().unbind()
        details.textProperty().bind(
            Bindings.createStringBinding(
                {
                    val size = model.sizeProperty.get().takeIf { model.size >= 0L }
                    when (model.status) {
                        Status.FINISHED.name -> listOfNotNull("Downloaded", size).joinToString(" • ")
                        Status.DOWNLOADING.name -> {
                            val percent = (model.progress.coerceIn(0.0, 1.0) * 100.0).roundToInt()
                            listOfNotNull("Downloading $percent%", size).joinToString(" • ")
                        }
                        Status.ERROR.name -> "Error"
                        Status.STOPPED.name -> "Stopped"
                        Status.PENDING.name -> "Pending"
                        else -> model.status.ifBlank { "Pending" }
                    }
                },
                model.statusProperty,
                model.progressProperty,
                model.sizeProperty
            )
        )
        tooltip.textProperty().unbind()
        tooltip.textProperty().bind(
            Bindings.createStringBinding(
                { model.filename.ifBlank { "Image ${model.index}" } },
                model.filenameProperty,
                model.indexProperty
            )
        )
        val remaining = remainingFraction(model)
        coverage.set(remaining)
        target.set(remaining)
        shade.minWidthProperty().unbind()
        shade.prefWidthProperty().unbind()
        shade.maxWidthProperty().unbind()
        val shadeWidth = Bindings.createDoubleBinding(
            { box.width * coverage.get() },
            coverage,
            box.widthProperty()
        )
        shade.minWidthProperty().bind(shadeWidth)
        shade.prefWidthProperty().bind(shadeWidth)
        shade.maxWidthProperty().bind(shadeWidth)
        val listener = InvalidationListener {
            target.set(remainingFraction(model))
            startShadeAnimator()
        }
        model.progressProperty.addListener(listener)
        model.statusProperty.addListener(listener)
        cell.properties["progressListener"] = listener
        cell.properties["boundModel"] = model
        cell.properties["boundThumbSize"] = thumbSize
        thumbView.image = thumbImages[model.id]
    }

    /**
     * Fraction of the tile still covered by the shade: the whole tile before
     * the download starts, the remaining progress while downloading and none
     * once the image is finished.
     */
    private fun remainingFraction(model: ImageModel): Double = when (model.status) {
        Status.FINISHED.name -> 0.0
        Status.DOWNLOADING.name -> 1.0 - model.progress.coerceIn(0.0, 1.0)
        else -> 1.0
    }

    /**
     * Single timer driving every tile shade. Progress events only move the
     * target; the displayed coverage eases towards it frame by frame, so the
     * transition stays smooth even when updates are frequent or irregular.
     */
    private fun startShadeAnimator() {
        if (shadeAnimator != null) return
        lastShadePulseNanos = 0L
        shadeAnimator = object : AnimationTimer() {
            override fun handle(now: Long) {
                if (lastShadePulseNanos == 0L) {
                    lastShadePulseNanos = now
                    return
                }
                val dt = ((now - lastShadePulseNanos) / 1_000_000_000.0).coerceIn(0.001, 0.1)
                lastShadePulseNanos = now
                var animating = false
                gridCells.forEach { cell ->
                    val coverage = cell.properties["shadeCoverage"] as? SimpleDoubleProperty
                        ?: return@forEach
                    val target = cell.properties["shadeTarget"] as? SimpleDoubleProperty
                        ?: return@forEach
                    val current = coverage.get()
                    val goal = target.get()
                    if (abs(goal - current) < 1e-4) {
                        if (current != goal) coverage.set(goal)
                        return@forEach
                    }
                    animating = true
                    // Time-based ease-out, independent of the frame rate.
                    val factor = 1.0 - exp(-dt / 0.10)
                    val next = current + (goal - current) * factor
                    coverage.set(if (abs(goal - next) < 1e-5) goal else next)
                }
                if (!animating) {
                    stopShadeAnimator()
                }
            }
        }
        shadeAnimator?.start()
    }

    private fun stopShadeAnimator() {
        shadeAnimator?.stop()
        shadeAnimator = null
        lastShadePulseNanos = 0L
    }

    private fun detachProgressAnimation(cell: Region) {
        val listener = cell.properties["progressListener"] as? InvalidationListener
        val model = cell.properties["boundModel"] as? ImageModel
        if (listener != null && model != null) {
            model.progressProperty.removeListener(listener)
            model.statusProperty.removeListener(listener)
        }
        cell.properties.remove("progressListener")
        cell.properties.remove("boundModel")
        cell.properties.remove("boundThumbSize")
        // Recycled cells must not keep animating while hidden.
        (cell.properties["shadeCoverage"] as? SimpleDoubleProperty)?.let { coverage ->
            (cell.properties["shadeTarget"] as? SimpleDoubleProperty)?.set(coverage.get())
        }
    }

    private fun selectOverviewIndex(imagesIndex: Int) {
        val model = workspaceState?.images?.getOrNull(imagesIndex) ?: return
        val sourceIndex = sources.indexOfFirst { it.imageId == model.id }
        if (sourceIndex < 0) return
        browseIndex = sourceIndex
        grid?.selectedIndex = imagesIndex
        if (table?.selectionModel?.selectedIndex != imagesIndex) {
            table?.selectionModel?.clearAndSelect(imagesIndex)
        }
    }

    private fun activateOverviewIndex(imagesIndex: Int) {
        workspaceState?.images?.getOrNull(imagesIndex)?.let { activateModel(it) }
    }

    private fun activateModel(model: ImageModel) {
        val sourceIndex = sources.indexOfFirst { it.imageId == model.id }
        if (sourceIndex >= 0) onActivate(sourceIndex)
    }

    /**
     * Loads every missing thumbnail in the background (bounded concurrency),
     * caching the encoded bytes through [ImageCacheManager]. Each image is
     * requested once.
     */
    private fun ensureThumbs() {
        val models = workspaceState?.images ?: return
        val pending = models.filter { it.thumbUrl.isNotEmpty() && thumbsRequested.add(it.id) }
        if (pending.isEmpty()) return
        val next = AtomicInteger()
        repeat(min(THUMB_CONCURRENCY, pending.size)) {
            scope.launch {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val position = next.getAndIncrement()
                    if (position >= pending.size) break
                    val model = pending[position]
                    val bytes = runCatching {
                        ImageCacheManager.load(model.postEntityId, model.thumbUrl)
                    }.getOrNull() ?: continue
                    if (bytes.isEmpty()) continue
                    currentCoroutineContext().ensureActive()
                    val image = runCatching {
                        ByteArrayInputStream(bytes).use { Image(it) }
                    }.getOrNull() ?: continue
                    runLater {
                        thumbImages[model.id] = image
                        grid?.refreshVisible()
                        table?.refresh()
                    }
                }
            }
        }
    }

    /**
     * Marks the highlighted thumbnail/row as selected (and clears the previous
     * selection). Does not scroll: a plain click must not move the list.
     */
    private fun highlightTile() {
        val source = sources.getOrNull(browseIndex)
        val imagesIndex =
            if (source == null) -1 else workspaceState?.images?.indexOfFirst { it.id == source.imageId } ?: -1
        grid?.selectedIndex = imagesIndex
        if (imagesIndex >= 0 && table?.selectionModel?.selectedIndex != imagesIndex) {
            table?.selectionModel?.clearAndSelect(imagesIndex)
        }
    }

    /**
     * Scrolls the highlighted entry into view, and only when it is not already
     * visible; used by keyboard/toolbar navigation, never by a plain click.
     */
    private fun scrollTileIntoView() {
        val source = sources.getOrNull(browseIndex) ?: return
        val imagesIndex = workspaceState?.images?.indexOfFirst { it.id == source.imageId } ?: return
        if (imagesIndex < 0) return
        runLater {
            if (layout == "TABLE") {
                table?.scrollTo(imagesIndex)
            } else {
                grid?.scrollToIndex(imagesIndex)
            }
        }
    }

    private fun applyThumbSize() {
        val side = thumbSize + THUMB_CELL_PADDING * 2.0
        grid?.apply {
            cellWidth = side
            cellHeight = side + THUMB_CAPTION_HEIGHT
            refreshAll()
        }
    }

    companion object {
        const val THUMB_SIZE = 200.0
        const val MIN_THUMB_SIZE = 80.0
        const val MAX_THUMB_SIZE = 400.0

        private const val THUMB_CONCURRENCY = 4
        private const val THUMB_CELL_PADDING = 8.0
        private const val THUMB_CAPTION_HEIGHT = 40.0
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
