package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import javafx.beans.binding.Bindings
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.ProgressBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.SelectionMode
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.gui.components.PostsPlaceholder
import me.vripper.gui.components.fragments.VirtualGridView
import me.vripper.gui.controller.PostsTabController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.AppThemeManager
import me.vripper.gui.utils.ImageCacheManager
import me.vripper.gui.utils.Preview
import me.vripper.gui.utils.showPostPreviewOnHover
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.View
import tornadofx.runLater
import kotlin.math.max
import java.io.ByteArrayInputStream

class PostsGridView : View("Posts") {

    override val root = StackPane()

    private val postsController: PostsTabController by inject()
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preview = Preview(currentStage!!)

    private lateinit var grid: VirtualGridView
    private lateinit var scroll: ScrollPane

    private val tiles = mutableSetOf<PostTile>()
    private var syncingSelection = false

    private var tileSize = DEFAULT_TILE_SIZE
    private var previewWidth = previewWidthFor(tileSize)
    private var previewHeight = previewHeightFor(previewWidth)
    private var previewPopupEnabled = true

    init {
        grid = VirtualGridView { postsController.items.size }.apply {
            cellWidth = tileSize
            cellHeight = previewHeight + captionHeightFor(widgetsController.currentSettings.fontSize)
            hgap = 8.0
            vgap = 8.0
            cellStyle = TILE_STYLE
            selectedCellStyle = TILE_SELECTED_STYLE
            selectionMode = SelectionMode.MULTIPLE
            cellFactory = {
                PostTile(coroutineScope, preview) { previewPopupEnabled }.also { tile ->
                    tiles.add(tile)
                    tile.setPreviewSize(previewWidth, previewHeight)
                }
            }
            cellBinder = { cell, index ->
                (cell as? PostTile)?.bind(postsController.items.getOrNull(index))
            }
            onSelectionChange = { indices -> publishSelection(indices) }
            onActivate = { index ->
                postsController.items.getOrNull(index)?.let { postsController.openOverview(it) }
            }
            contextMenuProvider = { index ->
                postsController.buildContextMenu { postsController.items.getOrNull(index) }
            }
        }

        scroll = ScrollPane(grid).apply {
            isFitToWidth = true
            isFitToHeight = false
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
            style = "-fx-background-color: transparent;"
            setOnMousePressed { requestFocus() }
            addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                if (event.code == KeyCode.DELETE && postsController.selectedPosts.isNotEmpty()) {
                    postsController.deleteSelected()
                    event.consume()
                }
            }
        }
        grid.attach(scroll)

        val placeholder = PostsPlaceholder(
            postsController.placeholderStateProperty,
            postsController.emptyMessage,
        ).apply {
            visibleProperty().bind(Bindings.isEmpty(postsController.items))
        }
        StackPane.setAlignment(placeholder, Pos.CENTER)
        with(root) {
            children.addAll(scroll, placeholder)
        }

        postsController.items.addListener(ListChangeListener {
            runLater {
                grid.refreshAll()
                syncSelectionFromController(requestFocus = false)
            }
        })
        widgetsController.currentSettings.fontSizeProperty.addListener { _, _, _ ->
            grid.cellHeight = previewHeight + captionHeightFor(widgetsController.currentSettings.fontSize)
            grid.refreshAll()
        }
    }

    /** Enables or disables the hover preview for every tile. */
    fun setPreviewPopupEnabled(enabled: Boolean) {
        previewPopupEnabled = enabled
        if (!enabled) preview.cleanup()
    }

    /** Resizes every tile and rebuilds the virtual grid layout. */
    fun setTileSize(size: Double) {
        tileSize = size.coerceIn(MIN_TILE_SIZE, MAX_TILE_SIZE)
        previewWidth = previewWidthFor(tileSize)
        previewHeight = previewHeightFor(previewWidth)
        grid.cellWidth = tileSize
        grid.cellHeight = previewHeight + captionHeightFor(widgetsController.currentSettings.fontSize)
        tiles.forEach { it.setPreviewSize(previewWidth, previewHeight) }
        grid.refreshAll()
    }

    fun syncSelectionFromController(requestFocus: Boolean = true) {
        syncingSelection = true
        try {
            val ids = postsController.selectedPostIds()
            val indices = LinkedHashSet<Int>()
            val visibleSelection = mutableListOf<PostModel>()
            var activeIndex = -1
            postsController.items.forEachIndexed { index, post ->
                if (post.id in ids) {
                    indices.add(index)
                    visibleSelection.add(post)
                }
                if (post.id == postsController.activePost?.id) activeIndex = index
            }
            if (activeIndex < 0 && indices.isNotEmpty()) activeIndex = indices.last()
            if (visibleSelection.size != ids.size) {
                postsController.setSelection(
                    visibleSelection,
                    postsController.activePost?.takeIf { it.id in ids } ?: visibleSelection.lastOrNull()
                )
            }
            grid.setSelectedIndices(indices, activeIndex)
            if (activeIndex >= 0) grid.scrollToIndex(activeIndex)
            if (requestFocus && root.isVisible) scroll.requestFocus()
        } finally {
            syncingSelection = false
        }
    }

    private fun publishSelection(indices: Set<Int>) {
        if (syncingSelection || !root.isVisible) return
        val posts = indices.mapNotNull { postsController.items.getOrNull(it) }
        val active = postsController.items.getOrNull(grid.selectedIndex)
        postsController.setSelection(posts, active)
    }

    private fun captionHeightFor(fontSize: Int): Double =
        CAPTION_HEIGHT + max(0.0, (fontSize - AppThemeManager.DEFAULT_FONT_SIZE) * 2.0)

    private class PostTile(
        private val scope: CoroutineScope,
        private val preview: Preview,
        private val isPreviewPopupEnabled: () -> Boolean,
    ) : VBox(4.0) {

        private val imageView = ImageView().apply {
            isPreserveRatio = true
            isSmooth = true
            isCache = true
        }
        private val imagePlaceholder = FontIcon.of(Feather.IMAGE).apply {
            styleClass.addAll(Styles.TEXT_MUTED, "vripper-icon-thumb")
        }
        private val previewBox = StackPane(imageView, imagePlaceholder).apply {
            alignment = Pos.CENTER
            style = "-fx-background-color: rgba(128,128,128,0.10);" +
                    "-fx-background-radius: 6; -fx-border-radius: 6;" +
                    "-fx-border-color: rgba(128,128,128,0.16); -fx-border-width: 1;"
        }
        private val titleLabel = Label().apply {
            alignment = Pos.CENTER
            maxWidth = Double.MAX_VALUE
            textOverrun = OverrunStyle.ELLIPSIS
            style = "-fx-font-size: 0.857em; -fx-font-weight: bold;"
        }
        private val detailsLabel = Label().apply {
            alignment = Pos.CENTER
            maxWidth = Double.MAX_VALUE
            textOverrun = OverrunStyle.ELLIPSIS
            style = "-fx-font-size: 0.714em; -fx-opacity: 0.72;"
        }
        private val progressBar = ProgressBar().apply {
            prefHeight = 4.0
            minHeight = 4.0
            maxHeight = 4.0
            maxWidth = Double.MAX_VALUE
            style = "-fx-padding: 0;"
        }
        private val tooltip = Tooltip()

        private var post: PostModel? = null
        private var previewJob: Job? = null

        init {
            alignment = Pos.TOP_CENTER
            children.addAll(previewBox, titleLabel, detailsLabel, progressBar)
            Tooltip.install(this, tooltip)
            previewBox.showPostPreviewOnHover(preview, isPreviewPopupEnabled) { post }
        }

        fun setPreviewSize(width: Double, height: Double) {
            imageView.fitWidth = width
            imageView.fitHeight = height
            previewBox.minWidth = width
            previewBox.prefWidth = width
            previewBox.maxWidth = width
            previewBox.minHeight = height
            previewBox.prefHeight = height
            previewBox.maxHeight = height
        }

        fun bind(newPost: PostModel?) {
            if (post === newPost) return
            unbind()
            post = newPost ?: return
            titleLabel.textProperty().bind(
                Bindings.createStringBinding(
                    {
                        newPost.title.ifBlank {
                            newPost.folderName.ifBlank { "Post ${newPost.vgPostId}" }
                        }
                    },
                    newPost.titleProperty,
                    newPost.folderNameProperty,
                )
            )
            detailsLabel.textProperty().bind(
                Bindings.createStringBinding(
                    {
                        val status = newPost.status.lowercase().replaceFirstChar { it.uppercase() }
                        listOf(status, newPost.progressCount)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                    },
                    newPost.statusProperty,
                    newPost.progressCountProperty,
                )
            )
            progressBar.progressProperty().bind(newPost.progressProperty)
            tooltip.textProperty().bind(titleLabel.textProperty())
            loadPreview(newPost)
        }

        private fun loadPreview(newPost: PostModel) {
            val url = newPost.previewList.firstOrNull { it.isNotBlank() } ?: return
            previewJob = scope.launch {
                val bytes = try {
                    ImageCacheManager.load(newPost.id, url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ByteArray(0)
                }
                val image = if (bytes.isEmpty()) {
                    null
                } else {
                    Image(ByteArrayInputStream(bytes))
                }
                runLater {
                    if (post === newPost) {
                        imageView.image = image
                        imagePlaceholder.isVisible = image == null
                    }
                }
            }
        }

        private fun unbind() {
            previewJob?.cancel()
            previewJob = null
            preview.cleanup()
            titleLabel.textProperty().unbind()
            detailsLabel.textProperty().unbind()
            tooltip.textProperty().unbind()
            progressBar.progressProperty().unbind()
            titleLabel.text = ""
            detailsLabel.text = ""
            tooltip.text = ""
            imageView.image = null
            imagePlaceholder.isVisible = true
            post = null
        }
    }

    companion object {
        const val MIN_TILE_SIZE = 140.0
        const val MAX_TILE_SIZE = 360.0
        const val DEFAULT_TILE_SIZE = 200.0

        private const val TILE_CHROME = 16.0
        private const val PREVIEW_RATIO = 0.8
        private const val CAPTION_HEIGHT = 70.0
        private const val TILE_STYLE =
            "-fx-background-color: rgba(128,128,128,0.08);" +
                    "-fx-background-radius: 6; -fx-border-radius: 6;" +
                    "-fx-border-color: transparent; -fx-border-width: 2; -fx-padding: 6;"
        private const val TILE_SELECTED_STYLE =
            "-fx-background-color: rgba(128,128,128,0.18);" +
                    "-fx-background-radius: 6; -fx-border-radius: 6;" +
                    "-fx-border-color: -color-accent-emphasis; -fx-border-width: 2; -fx-padding: 6;"

        private fun previewWidthFor(tileSize: Double): Double = tileSize - TILE_CHROME

        private fun previewHeightFor(previewWidth: Double): Double = previewWidth * PREVIEW_RATIO
    }
}
