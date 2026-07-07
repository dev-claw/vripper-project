package me.vripper.gui.components.views

import atlantafx.base.controls.ToggleSwitch
import javafx.animation.PauseTransition
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.Slider
import javafx.scene.control.SplitPane
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.*
import javafx.util.Duration
import kotlinx.coroutines.*
import me.vripper.gui.controller.PostsTabController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.utils.WidgetSettings
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class PostsView : View() {

    override val root: SplitPane = splitpane()

    private val tableFragment: PostsTableView by inject()
    private val listFragment: PostsListView by inject()
    private val gridFragment: PostsGridView by inject()
    private val postInfoView: PostInfoView by inject()
    private val postsController: PostsTabController by inject()
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var tableToggle: ToggleButton
    private lateinit var listToggle: ToggleButton
    private lateinit var gridToggle: ToggleButton
    private lateinit var tileSizeSlider: Slider
    private lateinit var previewPopupToggle: ToggleSwitch

    private val tileSizePersist = PauseTransition(Duration.millis(250.0)).apply {
        setOnFinished { WidgetSettings.update(widgetsController.currentSettings) }
    }

    init {
        val initialTileSize = widgetsController.currentSettings.postsGridTileSize.coerceIn(
            PostsGridView.MIN_TILE_SIZE,
            PostsGridView.MAX_TILE_SIZE,
        )
        widgetsController.currentSettings.postsGridTileSize = initialTileSize
        gridFragment.setTileSize(initialTileSize)
        val initialPreviewPopup = widgetsController.currentSettings.postsPreviewPopup
        gridFragment.setPreviewPopupEnabled(initialPreviewPopup)
        listFragment.setPreviewPopupEnabled(initialPreviewPopup)
        val content = StackPane().apply {
            add(tableFragment)
            add(listFragment)
            add(gridFragment)
        }
        VBox.setVgrow(content, Priority.ALWAYS)

        val top = VBox().apply {
            children.add(buildHeader())
            children.add(content)
        }

        with(root) {
            add(top)
            if (widgetsController.currentSettings.visibleInfoPanel) {
                postInfoView.setPostId(postsController.activePost?.id)
                add(postInfoView)
                setDividerPositions(widgetsController.currentSettings.infoPanelDividerPosition)
            }
            orientation = Orientation.VERTICAL
            vboxConstraints { vGrow = Priority.ALWAYS }
        }

        titleProperty.bind(postsController.items.sizeProperty.map {
            if (it.toLong() > 0) "Download (${it.toLong()})" else "Download"
        })

        postsController.activePostProperty.onChange { post ->
            if (widgetsController.currentSettings.visibleInfoPanel) {
                postInfoView.setPostId(post?.id)
            }
        }

        widgetsController.currentSettings.visibleInfoPanelProperty.onChange { visible ->
            if (visible) {
                postInfoView.setPostId(postsController.activePost?.id)
                root.add(postInfoView)
                root.setDividerPositions(widgetsController.currentSettings.infoPanelDividerPosition)
            } else {
                postInfoView.setPostId(null)
                root.items.removeIf { it.id == "postinfo_panel" }
            }
        }

        coroutineScope.launch {
            var lastDividerPosition = widgetsController.currentSettings.infoPanelDividerPosition
            while (isActive) {
                if (widgetsController.currentSettings.visibleInfoPanel) {
                    val dividerPosition = root.dividerPositions.firstOrNull()
                    if (dividerPosition != null && lastDividerPosition != dividerPosition) {
                        lastDividerPosition = dividerPosition
                        widgetsController.currentSettings.infoPanelDividerPosition = lastDividerPosition
                        WidgetSettings.update(widgetsController.currentSettings)
                    }
                }
                delay(1_000)
            }
        }

        val initialMode = when (widgetsController.currentSettings.postsViewMode) {
            "GRID" -> "GRID"
            "LIST" -> "LIST"
            else -> "TABLE"
        }
        setMode(initialMode, persist = false)
    }

    private fun buildHeader(): Region {
        val group = ToggleGroup()
        tableToggle = ToggleButton("Table").apply {
            graphic = FontIcon.of(Feather.COLUMNS)
            toggleGroup = group
            tooltip("Show posts as a table")
            setOnAction { setMode("TABLE") }
        }
        listToggle = ToggleButton("List").apply {
            graphic = FontIcon.of(Feather.ALIGN_LEFT)
            toggleGroup = group
            tooltip("Show posts as a relaxed list")
            setOnAction { setMode("LIST") }
        }
        gridToggle = ToggleButton("Grid").apply {
            graphic = FontIcon.of(Feather.GRID)
            toggleGroup = group
            tooltip("Show posts as a grid")
            setOnAction { setMode("GRID") }
        }
        val initialSize = widgetsController.currentSettings.postsGridTileSize.coerceIn(
            PostsGridView.MIN_TILE_SIZE,
            PostsGridView.MAX_TILE_SIZE,
        )
        tileSizeSlider = Slider(
            PostsGridView.MIN_TILE_SIZE,
            PostsGridView.MAX_TILE_SIZE,
            initialSize,
        ).apply {
            prefWidth = 160.0
            blockIncrement = 10.0
            tooltip("Tile size")
            valueProperty().addListener { _, _, value ->
                val size = value.toDouble()
                gridFragment.setTileSize(size)
                widgetsController.currentSettings.postsGridTileSize = size
                tileSizePersist.playFromStart()
            }
        }
        previewPopupToggle = ToggleSwitch("Previews").apply {
            isSelected = widgetsController.currentSettings.postsPreviewPopup
            tooltip("Show image previews when hovering a grid tile or list row")
            selectedProperty().onChange { enabled ->
                widgetsController.currentSettings.postsPreviewPopup = enabled
                gridFragment.setPreviewPopupEnabled(enabled)
                listFragment.setPreviewPopupEnabled(enabled)
            }
        }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        return HBox(
            6.0,
            tableToggle,
            listToggle,
            gridToggle,
            spacer,
            previewPopupToggle,
            tileSizeSlider,
        ).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(6.0, 8.0, 6.0, 8.0)
            style = "-fx-border-color: rgba(128,128,128,0.25); -fx-border-width: 0 0 1 0;"
        }
    }

    private fun setMode(mode: String, persist: Boolean = true) {
        val tableMode = mode == "TABLE"
        val listMode = mode == "LIST"
        val gridMode = mode == "GRID"
        tableToggle.isSelected = tableMode
        listToggle.isSelected = listMode
        gridToggle.isSelected = gridMode
        tableFragment.root.isVisible = tableMode
        tableFragment.root.isManaged = tableMode
        listFragment.root.isVisible = listMode
        listFragment.root.isManaged = listMode
        gridFragment.root.isVisible = gridMode
        gridFragment.root.isManaged = gridMode
        tileSizeSlider.isVisible = gridMode
        tileSizeSlider.isManaged = gridMode
        previewPopupToggle.isVisible = !tableMode
        previewPopupToggle.isManaged = !tableMode
        if (persist) {
            widgetsController.currentSettings.postsViewMode = mode
        }
        when {
            gridMode -> gridFragment.syncSelectionFromController()
            listMode -> listFragment.syncSelectionFromController()
            else -> tableFragment.syncSelectionFromController()
        }
    }
}
