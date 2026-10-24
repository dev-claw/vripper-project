package me.vripper.gui.components.views

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.Callback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.entities.Status
import me.vripper.gui.components.PostsPlaceholder
import me.vripper.gui.components.SmoothProgressBar
import me.vripper.gui.controller.PostsTabController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.AppThemeManager
import me.vripper.gui.utils.ImageCacheManager
import me.vripper.gui.utils.Preview
import me.vripper.gui.utils.displayLabel
import me.vripper.gui.utils.displayStatusLabel
import me.vripper.gui.utils.parseStatus
import me.vripper.gui.utils.showPostPreviewOnHover
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.View
import tornadofx.listview
import tornadofx.runLater
import tornadofx.vbox
import java.io.ByteArrayInputStream
import kotlin.math.max

/**
 * Relaxed row-based view of the download list. It exposes the same selection,
 * activation and context-menu behaviour as the table and grid while giving each
 * post more room for its thumbnail and metadata.
 */
class PostsListView : View("Posts") {

    override val root = vbox {}

    private val postsController: PostsTabController by inject()
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preview = Preview(currentStage!!)

    private lateinit var listView: ListView<PostModel>
    private var syncingSelection = false
    private var previewPopupEnabled = true

    init {
        listView = listview(postsController.items) {
            styleClass.add(Tweaks.EDGE_TO_EDGE)
            fixedCellSize = rowHeightFor(widgetsController.currentSettings.fontSize)
            selectionModel.selectionMode = SelectionMode.MULTIPLE
            style = "-fx-background-color: transparent;"
            placeholder = PostsPlaceholder(
                postsController.placeholderStateProperty,
                postsController.emptyMessage,
            )
            cellFactory = Callback {
                PostsListCell(coroutineScope, preview) { previewPopupEnabled }.also { cell ->
                    cell.setOnMouseClicked { event ->
                        if (event.clickCount == 2) {
                            cell.item?.let { postsController.openOverview(it) }
                        }
                    }
                    cell.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
                        if (event.button == MouseButton.SECONDARY &&
                            !cell.isEmpty &&
                            !cell.isSelected
                        ) {
                            selectionModel.clearSelection()
                            selectionModel.select(cell.index)
                            listView.focusModel.focus(cell.index)
                        }
                    }
                    cell.contextMenu = postsController.buildContextMenu { cell.item }
                }
            }
            addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                if (event.code == KeyCode.DELETE && selectionModel.selectedItems.isNotEmpty()) {
                    postsController.deleteSelected()
                    event.consume()
                }
            }
        }
        root.add(listView)
        VBox.setVgrow(listView, Priority.ALWAYS)
        widgetsController.currentSettings.fontSizeProperty.addListener { _, _, _ ->
            listView.fixedCellSize = rowHeightFor(widgetsController.currentSettings.fontSize)
        }
        listView.selectionModel.selectedItems.addListener(ListChangeListener { publishSelection() })
        listView.selectionModel.selectedItemProperty().addListener { _, _, _ -> publishSelection() }
        publishSelection()
    }

    /** Enables or disables the hover preview for every row. */
    fun setPreviewPopupEnabled(enabled: Boolean) {
        previewPopupEnabled = enabled
        if (!enabled) preview.cleanup()
    }

    fun syncSelectionFromController(requestFocus: Boolean = true) {
        syncingSelection = true
        try {
            val ids = postsController.selectedPostIds()
            val model = postsController.items
            listView.selectionModel.clearSelection()
            var activeIndex = -1
            val visibleSelection = mutableListOf<PostModel>()
            model.forEachIndexed { index, post ->
                if (post.id in ids) {
                    listView.selectionModel.select(index)
                    visibleSelection.add(post)
                }
                if (post.id == postsController.activePost?.id) {
                    activeIndex = index
                }
            }
            if (activeIndex < 0 && ids.isNotEmpty()) {
                activeIndex = model.indexOfFirst { it.id in ids }
            }
            if (activeIndex >= 0) {
                listView.focusModel.focus(activeIndex)
            }
            if (visibleSelection.size != ids.size) {
                postsController.setSelection(
                    visibleSelection,
                    postsController.activePost?.takeIf { it.id in ids } ?: visibleSelection.lastOrNull()
                )
            }
            if (requestFocus) listView.requestFocus()
        } finally {
            syncingSelection = false
        }
    }

    private fun publishSelection() {
        if (syncingSelection || !root.isVisible) return
        val selected = listView.selectionModel.selectedItems.toList()
        val active = listView.selectionModel.selectedItem ?: selected.lastOrNull()
        postsController.setSelection(selected, active)
    }

    private fun rowHeightFor(fontSize: Int): Double =
        ROW_HEIGHT + max(0.0, (fontSize - AppThemeManager.DEFAULT_FONT_SIZE) * 1.5)

    private companion object {
        const val ROW_HEIGHT = 112.0
    }

    private class PostsListCell(
        private val scope: CoroutineScope,
        private val preview: Preview,
        private val isPreviewPopupEnabled: () -> Boolean,
    ) : ListCell<PostModel>() {

        private val thumbnail = ImageView().apply {
            fitWidth = THUMBNAIL_WIDTH
            fitHeight = THUMBNAIL_HEIGHT
            isPreserveRatio = true
            isSmooth = true
            isCache = true
        }
        private val thumbnailPlaceholder = FontIcon.of(Feather.IMAGE).apply {
            styleClass.addAll(Styles.TEXT_MUTED, "vripper-icon-thumb")
        }
        private val thumbnailBox = StackPane(thumbnail, thumbnailPlaceholder).apply {
            minWidth = THUMBNAIL_WIDTH
            prefWidth = THUMBNAIL_WIDTH
            maxWidth = THUMBNAIL_WIDTH
            minHeight = THUMBNAIL_HEIGHT
            prefHeight = THUMBNAIL_HEIGHT
            maxHeight = THUMBNAIL_HEIGHT
            style = "-fx-background-color: rgba(128,128,128,0.10);" +
                    "-fx-background-radius: 6; -fx-border-radius: 6;" +
                    "-fx-border-color: rgba(128,128,128,0.16); -fx-border-width: 1;"
        }
        private val titleLabel = Label().apply {
            maxWidth = Double.MAX_VALUE
            textOverrun = OverrunStyle.ELLIPSIS
            style = "-fx-font-size: 0.93em; -fx-font-weight: bold;"
        }
        private val statusIcon = FontIcon.of(Feather.CLOCK).apply {
            styleClass.add("vripper-icon-status")
        }
        private val statusLabel = Label().apply {
            style = "-fx-font-size: 0.714em;"
        }
        private val statusBadge = HBox(4.0, statusIcon, statusLabel).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(2.0, 8.0, 2.0, 8.0)
            style = "-fx-background-color: rgba(128,128,128,0.12); -fx-background-radius: 10;"
        }
        private val progressCountLabel = Label().apply {
            textOverrun = OverrunStyle.ELLIPSIS
            style = "-fx-font-size: 0.714em; -fx-opacity: 0.78;"
        }
        private val hostsLabel = Label().apply {
            maxWidth = 260.0
            textOverrun = OverrunStyle.ELLIPSIS
            style = "-fx-font-size: 0.714em; -fx-opacity: 0.62;"
        }
        private val metaRow = HBox(10.0, statusBadge, progressCountLabel, hostsLabel).apply {
            alignment = Pos.CENTER_LEFT
        }
        private val pathLabel = Label().apply {
            maxWidth = Double.MAX_VALUE
            textOverrun = OverrunStyle.ELLIPSIS
            styleClass.add(Styles.TEXT_MUTED)
            style = "-fx-font-size: 0.714em;"
        }
        private val centerBox = VBox(3.0, titleLabel, metaRow, pathLabel).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
        private val addedOnLabel = Label().apply {
            styleClass.add(Styles.TEXT_MUTED)
            style = "-fx-font-size: 0.714em;"
        }
        private val authorLabel = Label().apply {
            styleClass.add(Styles.TEXT_MUTED)
            style = "-fx-font-size: 0.714em;"
        }
        private val authorIcon = FontIcon.of(Feather.USER).apply {
            styleClass.add("vripper-icon-meta")
        }
        private val authorBox = HBox(4.0, authorIcon, authorLabel).apply {
            alignment = Pos.CENTER_RIGHT
            styleClass.add(Styles.TEXT_MUTED)
        }
        private val rightBox = VBox(4.0, addedOnLabel, authorBox).apply {
            alignment = Pos.TOP_RIGHT
            minWidth = 150.0
        }
        private val progressBar = SmoothProgressBar().apply {
            prefHeight = 4.0
            minHeight = 4.0
            maxHeight = 4.0
            maxWidth = Double.MAX_VALUE
            style = "-fx-padding: 0;"
        }
        private val mainRow = HBox(12.0, thumbnailBox, centerBox, rightBox).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
        private val row = VBox(6.0, mainRow, progressBar).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
            isPickOnBounds = true
            padding = Insets(9.0, 12.0, 9.0, 12.0)
        }
        private val titleTooltip = Tooltip()
        private val pathTooltip = Tooltip()

        private var boundItem: PostModel? = null
        private var thumbnailJob: Job? = null

        private val statusListener = InvalidationListener {
            boundItem?.let(::updateStatus)
        }
        private val progressListener = InvalidationListener {
            boundItem?.let { progressBar.animateProgress(it.progress) }
        }

        init {
            HBox.setHgrow(centerBox, Priority.ALWAYS)
            padding = Insets(3.0, 8.0, 3.0, 8.0)
            graphic = row
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            alignment = Pos.CENTER_LEFT
            row.prefWidthProperty().bind(widthProperty().subtract(16.0))
            row.maxWidthProperty().bind(row.prefWidthProperty())
            Tooltip.install(row, titleTooltip)
            Tooltip.install(pathLabel, pathTooltip)
            thumbnailBox.showPostPreviewOnHover(preview, isPreviewPopupEnabled) { boundItem }
            selectedProperty().addListener { _, _, _ -> applyRowStyle() }
            row.setOnMouseEntered { if (!isSelected) row.style = HOVER_ROW_STYLE }
            row.setOnMouseExited { applyRowStyle() }
            applyRowStyle()
        }

        override fun updateItem(item: PostModel?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == null) {
                unbind()
                graphic = null
                return
            }
            if (item === boundItem) {
                applyRowStyle()
                return
            }
            unbind()
            boundItem = item
            graphic = row
            titleLabel.textProperty().bind(
                Bindings.createStringBinding(
                    {
                        item.title.ifBlank {
                            item.folderName.ifBlank { "Post ${item.vgPostId}" }
                        }
                    },
                    item.titleProperty,
                    item.folderNameProperty,
                )
            )
            progressCountLabel.textProperty().bind(item.progressCountProperty)
            hostsLabel.textProperty().bind(
                Bindings.createStringBinding(
                    { item.hosts.takeIf { it.isNotBlank() }?.let { "via $it" } ?: "" },
                    item.hostsProperty,
                )
            )
            hostsLabel.visibleProperty().bind(hostsLabel.textProperty().isNotEmpty)
            hostsLabel.managedProperty().bind(hostsLabel.visibleProperty())
            pathLabel.textProperty().bind(
                Bindings.createStringBinding(
                    { item.folderName.ifBlank { item.path } },
                    item.folderNameProperty,
                    item.pathProperty,
                )
            )
            addedOnLabel.textProperty().bind(item.addedOnProperty)
            authorLabel.textProperty().bind(item.postedByProperty)
            authorBox.visibleProperty().bind(
                Bindings.createBooleanBinding({ item.postedBy.isNotBlank() }, item.postedByProperty)
            )
            authorBox.managedProperty().bind(authorBox.visibleProperty())
            progressBar.stopAnimation()
            progressBar.progress = item.progress
            item.progressProperty.addListener(progressListener)
            titleTooltip.textProperty().bind(titleLabel.textProperty())
            pathTooltip.textProperty().bind(item.pathProperty)
            item.statusProperty.addListener(statusListener)
            updateStatus(item)
            loadThumbnail(item)
            applyRowStyle()
        }

        private fun unbind() {
            thumbnailJob?.cancel()
            thumbnailJob = null
            preview.cleanup()
            boundItem?.statusProperty?.removeListener(statusListener)
            boundItem?.progressProperty?.removeListener(progressListener)
            titleLabel.textProperty().unbind()
            progressCountLabel.textProperty().unbind()
            hostsLabel.textProperty().unbind()
            hostsLabel.visibleProperty().unbind()
            hostsLabel.managedProperty().unbind()
            pathLabel.textProperty().unbind()
            addedOnLabel.textProperty().unbind()
            authorLabel.textProperty().unbind()
            authorBox.visibleProperty().unbind()
            authorBox.managedProperty().unbind()
            progressBar.stopAnimation()
            titleTooltip.textProperty().unbind()
            pathTooltip.textProperty().unbind()
            thumbnail.image = null
            thumbnailPlaceholder.isVisible = true
            titleLabel.text = ""
            progressCountLabel.text = ""
            hostsLabel.text = ""
            hostsLabel.isVisible = false
            hostsLabel.isManaged = false
            pathLabel.text = ""
            addedOnLabel.text = ""
            authorLabel.text = ""
            authorBox.isVisible = false
            authorBox.isManaged = false
            boundItem = null
        }

        private fun loadThumbnail(item: PostModel) {
            val url = item.previewList.firstOrNull { it.isNotBlank() } ?: return
            thumbnailJob = scope.launch {
                val bytes = try {
                    ImageCacheManager.load(item.id, url)
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
                    if (boundItem === item) {
                        thumbnail.image = image
                        thumbnailPlaceholder.isVisible = image == null
                    }
                }
            }
        }

        private fun updateStatus(item: PostModel) {
            val status = parseStatus(item.status)
            statusIcon.iconCode = when (status) {
                Status.PENDING -> Feather.CLOCK
                Status.DOWNLOADING -> Feather.DOWNLOAD
                Status.FINISHED -> Feather.CHECK
                Status.ERROR -> Feather.ALERT_TRIANGLE
                Status.STOPPED -> Feather.PAUSE
                null -> Feather.CLOCK
            }
            statusLabel.text = status?.displayLabel() ?: item.status.displayStatusLabel()
            statusIcon.styleClass.removeAll(STATUS_STYLE_CLASSES)
            statusLabel.styleClass.removeAll(STATUS_STYLE_CLASSES)
            val styleClass = when (status) {
                Status.DOWNLOADING -> Styles.ACCENT
                Status.FINISHED -> Styles.SUCCESS
                Status.ERROR -> Styles.DANGER
                Status.STOPPED -> Styles.WARNING
                Status.PENDING, null -> Styles.TEXT_MUTED
            }
            statusIcon.styleClass.add(styleClass)
            statusLabel.styleClass.add(styleClass)
        }

        private fun applyRowStyle() {
            row.style = if (isSelected) SELECTED_ROW_STYLE else NORMAL_ROW_STYLE
        }

        private companion object {
            const val THUMBNAIL_WIDTH = 96.0
            const val THUMBNAIL_HEIGHT = 72.0

            val STATUS_STYLE_CLASSES = listOf(
                Styles.ACCENT,
                Styles.SUCCESS,
                Styles.DANGER,
                Styles.WARNING,
                Styles.TEXT_MUTED,
            )

            const val BASE_ROW_STYLE =
                "-fx-background-radius: 8; -fx-border-radius: 8;" +
                        "-fx-border-width: 1; -fx-padding: 9 12;"
            const val NORMAL_ROW_STYLE =
                BASE_ROW_STYLE +
                        "-fx-background-color: rgba(128,128,128,0.06);" +
                        "-fx-border-color: rgba(128,128,128,0.12);"
            const val HOVER_ROW_STYLE =
                BASE_ROW_STYLE +
                        "-fx-background-color: rgba(128,128,128,0.11);" +
                        "-fx-border-color: rgba(128,128,128,0.20);"
            const val SELECTED_ROW_STYLE =
                BASE_ROW_STYLE +
                        "-fx-background-color: rgba(128,128,128,0.18);" +
                        "-fx-border-color: -color-accent-emphasis;"
        }
    }
}
