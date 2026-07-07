package me.vripper.gui.components.fragments

import javafx.scene.control.ContextMenu
import javafx.scene.control.ScrollPane
import javafx.scene.control.SelectionMode
import javafx.scene.input.MouseButton
import javafx.scene.layout.Region
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight virtualized grid: only the cells within (and just around) the
 * viewport exist as nodes, so a post with thousands of images stays cheap. The
 * grid must be the direct content of a [ScrollPane], attached via [attach].
 *
 * Cells are created through [cellFactory] and their content refreshed through
 * [cellBinder], which is called again whenever a recycled cell is bound to a
 * new index or when [refreshVisible] runs.
 */
class VirtualGridView(private val countProvider: () -> Int) : Region() {

    var cellWidth = 200.0
    var cellHeight = 200.0
    var hgap = 6.0
    var vgap = 6.0
    var cellStyle = ""
    var selectedCellStyle = ""
    var cellFactory: (() -> Region)? = null
    var cellBinder: ((Region, Int) -> Unit)? = null
    var onSelect: ((Int) -> Unit)? = null
    var onActivate: ((Int) -> Unit)? = null
    var onActivateCell: ((Int, Region) -> Unit)? = null
    var onSelectionChange: ((Set<Int>) -> Unit)? = null
    var contextMenuProvider: ((Int) -> ContextMenu?)? = null

    var selectionMode: SelectionMode = SelectionMode.SINGLE

    private val selected = LinkedHashSet<Int>()
    private var activeIndex = -1
    private var anchorIndex = -1

    var selectedIndex: Int
        get() = activeIndex
        set(value) {
            activeIndex = value
            selected.clear()
            if (value >= 0) selected.add(value)
            anchorIndex = value
            applySelection()
        }

    fun selectedIndices(): Set<Int> = selected.toSet()

    /**
     * Replaces the selection without notifying [onSelect] or
     * [onSelectionChange]. Used to mirror a selection owned by another view.
     */
    fun setSelectedIndices(indices: Collection<Int>, active: Int = -1) {
        val count = countProvider()
        selected.clear()
        indices.filterTo(selected) { it in 0 until count }
        activeIndex = if (active >= 0) active else selected.lastOrNull() ?: -1
        anchorIndex = activeIndex
        applySelection()
    }

    private val active = HashMap<Int, Region>()
    private val pool = ArrayDeque<Region>()
    private var scrollPane: ScrollPane? = null

    init {
        // The explicit pref height is what lets the ScrollPane know how tall the
        // grid is (and therefore when to show its vertical scrollbar).
        widthProperty().addListener { _, _, _ -> updateContentHeight() }
    }

    fun attach(scrollPane: ScrollPane) {
        this.scrollPane = scrollPane
        scrollPane.vvalueProperty().addListener { _, _, _ -> requestLayout() }
        scrollPane.viewportBoundsProperty().addListener { _, _, _ -> requestLayout() }
        scrollPane.widthProperty().addListener { _, _, _ -> requestLayout() }
    }

    /** Re-invokes the binder for every visible cell (e.g. after a thumb loads). */
    fun refreshVisible() {
        active.forEach { (index, cell) -> cellBinder?.invoke(cell, index) }
    }

    fun refreshAll() {
        recycleAll()
        updateContentHeight()
        requestLayout()
    }

    /**
     * Recomputes the total content height from the current width and item count
     * and writes it to the `prefHeight` property. The ScrollPane reads this to
     * size its viewport and show a vertical scrollbar.
     */
    private fun updateContentHeight() {
        val columns = columnsFor(width)
        val totalRows = rowsFor(columns)
        val h = if (totalRows <= 0) 0.0 else totalRows * cellHeight + max(0, totalRows - 1) * vgap
        if (prefHeight != h) prefHeight = h
    }

    /**
     * Scrolls so [index] is visible, keeping the current scroll offset when the
     * cell is already on screen.
     */
    fun scrollToIndex(index: Int) {
        val pane = scrollPane ?: return
        val columns = columnsFor(width)
        if (columns <= 0) return
        val totalRows = rowsFor(columns)
        val rowPitch = cellHeight + vgap
        val contentHeight = max(0.0, totalRows * cellHeight + max(0, totalRows - 1) * vgap)
        val viewportHeight = pane.viewportBounds.height
        if (contentHeight <= viewportHeight) return
        val row = index / columns
        val cellTop = row * rowPitch
        val cellBottom = cellTop + cellHeight
        val scrollable = contentHeight - viewportHeight
        val offset = pane.vvalue * scrollable
        when {
            cellTop < offset -> pane.vvalue = (cellTop / scrollable).coerceIn(0.0, 1.0)
            cellBottom > offset + viewportHeight ->
                pane.vvalue = ((cellBottom - viewportHeight) / scrollable).coerceIn(0.0, 1.0)
        }
    }

    override fun computeMinWidth(height: Double) = 0.0

    override fun computeMinHeight(width: Double) = 0.0

    override fun computePrefWidth(height: Double) = scrollPane?.width ?: 0.0

    override fun computePrefHeight(width: Double): Double {
        val columns = columnsFor(width)
        if (columns <= 0) return 0.0
        val totalRows = rowsFor(columns)
        if (totalRows <= 0) return 0.0
        return totalRows * cellHeight + max(0, totalRows - 1) * vgap
    }

    override fun layoutChildren() {
        val count = countProvider()
        if (count <= 0) {
            recycleAll()
            return
        }
        val columns = columnsFor(width)
        if (columns <= 0) {
            recycleAll()
            return
        }
        val totalRows = rowsFor(columns)
        val rowPitch = cellHeight + vgap
        val contentHeight = totalRows * cellHeight + max(0, totalRows - 1) * vgap
        val pane = scrollPane
        val viewportHeight = pane?.viewportBounds?.height ?: height
        val scrollable = max(0.0, contentHeight - viewportHeight)
        val scrollTop = (pane?.vvalue ?: 0.0) * scrollable

        val firstRow = max(0, floor(scrollTop / rowPitch).toInt() - 1)
        val lastRow = min(totalRows - 1, ceil((scrollTop + viewportHeight) / rowPitch).toInt() + 1)
        val firstIndex = firstRow * columns
        val lastIndex = min(count - 1, (lastRow + 1) * columns - 1)

        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key < firstIndex || entry.key > lastIndex) {
                children.remove(entry.value)
                pool.addLast(entry.value)
                iterator.remove()
            }
        }

        for (index in firstIndex..lastIndex) {
            var cell = active[index]
            if (cell == null) {
                cell = if (pool.isEmpty()) createCell() else pool.removeLast()
                active[index] = cell
                children.add(cell)
            }
            bindCell(cell, index)
            val row = index / columns
            val col = index % columns
            cell.resizeRelocate(col * (cellWidth + hgap), row * rowPitch, cellWidth, cellHeight)
        }
    }

    private fun createCell(): Region {
        val cell = cellFactory?.invoke() ?: Region()
        cell.setOnMouseClicked { event ->
            val index = cell.properties["vgIndex"] as? Int ?: return@setOnMouseClicked
            if (event.button == MouseButton.PRIMARY) {
                if (event.clickCount == 2) {
                    onActivateCell?.invoke(index, cell) ?: onActivate?.invoke(index)
                } else {
                    applyClickSelection(index, event.isControlDown || event.isMetaDown, event.isShiftDown)
                    onSelect?.invoke(index)
                    onSelectionChange?.invoke(selected.toSet())
                }
                event.consume()
            }
        }
        cell.setOnContextMenuRequested { event ->
            val index = cell.properties["vgIndex"] as? Int ?: return@setOnContextMenuRequested
            if (index !in selected) {
                selectOnly(index)
                onSelect?.invoke(index)
                onSelectionChange?.invoke(selected.toSet())
            }
            contextMenuProvider?.invoke(index)?.show(cell, event.screenX, event.screenY)
            event.consume()
        }
        return cell
    }

    private fun applyClickSelection(index: Int, controlDown: Boolean, shiftDown: Boolean) {
        when {
            selectionMode != SelectionMode.MULTIPLE -> selectOnly(index)
            shiftDown -> {
                val count = countProvider()
                if (anchorIndex !in 0 until count) anchorIndex = index
                val from = min(anchorIndex, index)
                val to = max(anchorIndex, index)
                selected.clear()
                for (i in from..to) selected.add(i)
                activeIndex = index
            }

            controlDown -> {
                if (!selected.remove(index)) selected.add(index)
                activeIndex = index
                anchorIndex = index
            }

            else -> selectOnly(index)
        }
        applySelection()
    }

    private fun selectOnly(index: Int) {
        selected.clear()
        selected.add(index)
        activeIndex = index
        anchorIndex = index
        applySelection()
    }

    private fun bindCell(cell: Region, index: Int) {
        cell.properties["vgIndex"] = index
        cellBinder?.invoke(cell, index)
        cell.style = if (index in selected) selectedCellStyle else cellStyle
    }

    private fun applySelection() {
        active.forEach { (index, cell) ->
            cell.style = if (index in selected) selectedCellStyle else cellStyle
        }
    }

    private fun recycleAll() {
        active.forEach { (_, cell) ->
            children.remove(cell)
            pool.addLast(cell)
        }
        active.clear()
    }

    fun dispose() {
        recycleAll()
        pool.clear()
        selected.clear()
        activeIndex = -1
        anchorIndex = -1
        scrollPane = null
    }

    private fun columnsFor(width: Double): Int {
        if (width <= 0.0) return 0
        return max(1, floor((width + hgap) / (cellWidth + hgap)).toInt())
    }

    private fun rowsFor(columns: Int): Int {
        val count = countProvider()
        if (count <= 0 || columns <= 0) return 0
        return ceil(count / columns.toDouble()).toInt()
    }
}
