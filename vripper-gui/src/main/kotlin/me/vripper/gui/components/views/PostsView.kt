package me.vripper.gui.components.views

import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.SplitPane
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import me.vripper.gui.components.quickFilterPills
import me.vripper.gui.controller.PostQuickFilter
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

    init {
        val settings = widgetsController.currentSettings
        val initialTileSize = settings.postsGridTileSize.coerceIn(
            PostsGridView.MIN_TILE_SIZE,
            PostsGridView.MAX_TILE_SIZE,
        )
        settings.postsGridTileSize = initialTileSize
        gridFragment.setTileSize(initialTileSize)
        gridFragment.setPreviewPopupEnabled(settings.postsPreviewPopup)
        listFragment.setPreviewPopupEnabled(settings.postsPreviewPopup)

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
            if (settings.visibleInfoPanel) {
                postInfoView.setPostId(postsController.activePost?.id)
                add(postInfoView)
                setDividerPositions(settings.infoPanelDividerPosition)
            }
            orientation = Orientation.VERTICAL
            vboxConstraints { vGrow = Priority.ALWAYS }
        }

        titleProperty.bind(postsController.items.sizeProperty.map {
            if (it.toLong() > 0) "Download (${it.toLong()})" else "Download"
        })

        postsController.activePostProperty.onChange { post ->
            if (settings.visibleInfoPanel) {
                postInfoView.setPostId(post?.id)
            }
        }

        settings.visibleInfoPanelProperty.onChange { visible ->
            if (visible) {
                postInfoView.setPostId(postsController.activePost?.id)
                root.add(postInfoView)
                root.setDividerPositions(settings.infoPanelDividerPosition)
            } else {
                postInfoView.setPostId(null)
                root.items.removeIf { it.id == "postinfo_panel" }
            }
        }

        settings.postsGridTileSizeProperty.onChange { value ->
            val size = value.toDouble().coerceIn(PostsGridView.MIN_TILE_SIZE, PostsGridView.MAX_TILE_SIZE)
            if (size != value.toDouble()) {
                settings.postsGridTileSize = size
            }
            gridFragment.setTileSize(size)
        }

        settings.postsPreviewPopupProperty.onChange { enabled ->
            gridFragment.setPreviewPopupEnabled(enabled)
            listFragment.setPreviewPopupEnabled(enabled)
        }

        settings.postsViewModeProperty.onChange { mode -> setMode(mode.orEmpty(), persist = false) }
        val initialMode = when (settings.postsViewMode) {
            "GRID" -> "GRID"
            "LIST" -> "LIST"
            else -> "TABLE"
        }
        setMode(initialMode, persist = false)

        coroutineScope.launch {
            var lastDividerPosition = settings.infoPanelDividerPosition
            while (isActive) {
                if (settings.visibleInfoPanel) {
                    val dividerPosition = root.dividerPositions.firstOrNull()
                    if (dividerPosition != null && lastDividerPosition != dividerPosition) {
                        lastDividerPosition = dividerPosition
                        settings.infoPanelDividerPosition = lastDividerPosition
                        WidgetSettings.update(settings)
                    }
                }
                delay(1_000)
            }
        }
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
        val quickFilters = quickFilterPills(
            "Status",
            PostQuickFilter.entries.map { it to it.label },
            postsController.quickFilter,
        ) { postsController.quickFilter = it }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        return HBox(
            8.0,
            tableToggle,
            listToggle,
            gridToggle,
            spacer,
            quickFilters,
        ).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(6.0, 8.0, 6.0, 8.0)
        }
    }

    private fun setMode(mode: String, persist: Boolean = true) {
        val normalized = when (mode) {
            "LIST", "GRID" -> mode
            else -> "TABLE"
        }
        val tableMode = normalized == "TABLE"
        val listMode = normalized == "LIST"
        val gridMode = normalized == "GRID"
        tableToggle.isSelected = tableMode
        listToggle.isSelected = listMode
        gridToggle.isSelected = gridMode
        tableFragment.root.isVisible = tableMode
        tableFragment.root.isManaged = tableMode
        listFragment.root.isVisible = listMode
        listFragment.root.isManaged = listMode
        gridFragment.root.isVisible = gridMode
        gridFragment.root.isManaged = gridMode
        if (persist) {
            widgetsController.currentSettings.postsViewMode = normalized
        }
        when {
            gridMode -> gridFragment.syncSelectionFromController()
            listMode -> listFragment.syncSelectionFromController()
            else -> tableFragment.syncSelectionFromController()
        }
    }
}
