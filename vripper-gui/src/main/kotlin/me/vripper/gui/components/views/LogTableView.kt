package me.vripper.gui.components.views

import javafx.beans.binding.Bindings

import atlantafx.base.theme.Styles
import atlantafx.base.theme.Tweaks
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ButtonType
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollBar
import javafx.scene.control.ToggleButton
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.vripper.gui.components.EmptyState
import me.vripper.gui.components.LogQuickFilter
import me.vripper.gui.components.Shared
import me.vripper.gui.components.fragments.LogMessageFragment
import me.vripper.gui.components.quickFilterPills
import me.vripper.gui.controller.LogController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.LogModel
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import me.vripper.gui.fx.*
import java.util.function.Predicate

class LogTableView : View() {

    private val logController: LogController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchInput = Shared.logsSearchInput
    private val threadIdInput = Shared.logsThreadIdInput
    private val postIdInput = Shared.logsPostIdInput
    private val quickFilterProperty = Shared.logQuickFilter
    private val items: ObservableList<LogModel> = FXCollections.observableArrayList()
    private val filteredItems = FilteredList(items)
    private val listView: ListView<LogModel>
    private val loadingPlaceholder = Label("Loading")
    private val connectionFailurePlaceholder = Label("Connection Failure")
    private val emptyMessage = SimpleStringProperty("No logs yet")
    private val emptyPlaceholder = EmptyState.create(Feather.TERMINAL, emptyMessage)
    private var maxLogEvent = 100
    private val followTail = SimpleBooleanProperty(true)

    override val root = vbox {}

    init {
        with(root) {
            hbox {
                padding = Insets(8.0)
                spacing = 8.0
                alignment = Pos.CENTER_LEFT
                button("Clear logs", FontIcon.of(Feather.TRASH_2)) {
                    action { clearLogs() }
                }
                add(
                    ToggleButton("Follow", FontIcon.of(Feather.ARROW_DOWN)).apply {
                        addClass(Styles.SMALL)
                        isSelected = followTail.get()
                        selectedProperty().bindBidirectional(followTail)
                    }
                )
                textfield(threadIdInput) {
                    promptText = "Thread ID"
                    prefColumnCount = 8
                }
                textfield(postIdInput) {
                    promptText = "Post ID"
                    prefColumnCount = 8
                }
                spacer()
                add(
                    quickFilterPills(
                        "Level",
                        LogQuickFilter.entries.map { it to it.label },
                        quickFilterProperty.get(),
                    ) { quickFilterProperty.set(it) }
                )
            }
            listView = listview(filteredItems) {
                addClass(Styles.DENSE, Tweaks.EDGE_TO_EDGE)
                style = "-fx-font-family: monospace;"
                setCellFactory { TerminalLogCell() }
                setOnMouseClicked {
                    if (it.clickCount == 2) {
                        selectionModel.selectedItem?.let(::openLog)
                    }
                }
            }
            listView.contextMenu = ContextMenu().apply {
                items.add(MenuItem("Show details").apply {
                    setOnAction { listView.selectionModel.selectedItem?.let(::openLog) }
                })
                items.add(MenuItem("Copy").apply {
                    setOnAction { listView.selectionModel.selectedItem?.let(::copyLog) }
                })
            }
            VBox.setVgrow(listView, Priority.ALWAYS)
        }

        searchInput.addListener { _, _, _ -> applyLogFilter() }
        threadIdInput.addListener { _, _, _ -> applyLogFilter() }
        postIdInput.addListener { _, _, _ -> applyLogFilter() }
        quickFilterProperty.addListener { _, _, _ -> applyLogFilter() }
        applyLogFilter()
        titleProperty.bind(Bindings.size(filteredItems).map {
            if (it.toLong() > 0) {
                "Log (${it.toLong()})"
            } else {
                "Log"
            }
        })
        listView.placeholder = loadingPlaceholder
        listView.addEventFilter(ScrollEvent.SCROLL) { event ->
            if (event.deltaY > 0) {
                followTail.set(false)
            }
        }
        listView.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            when (event.code) {
                KeyCode.UP, KeyCode.PAGE_UP, KeyCode.HOME -> followTail.set(false)
                else -> {}
            }
        }
        followTail.addListener { _, _, follow ->
            if (follow) {
                scrollToBottom()
            }
        }
        listView.skinProperty().onChange {
            runLater {
                (listView.lookup(".scroll-bar:vertical") as? ScrollBar)?.let { scrollBar ->
                    scrollBar.addEventFilter(MouseEvent.MOUSE_PRESSED) {
                        followTail.set(false)
                    }
                }
            }
        }

        logController.newLogs.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    runLater {
                        while (items.isNotEmpty() && items.size >= maxLogEvent.coerceAtLeast(1)) {
                            items.removeFirst()
                        }
                        items.add(it)
                        if (followTail.get()) {
                            scrollToBottom()
                        }
                    }
                }
            }
        }

        logController.updateSettings.let { flow ->
            coroutineScope.launch {
                flow.collect {
                    maxLogEvent = it.systemSettings.maxEventLog
                    runLater {
                        while (items.size > maxLogEvent.coerceAtLeast(1)) {
                            items.removeFirst()
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            GuiEventBus.events.collect {
                when (it) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> {
                        while (isActive) {
                            val result = runCatching { logController.getMaxEventLog() }
                            if (result.isSuccess) {
                                maxLogEvent = result.getOrNull()!!
                                break
                            }
                        }
                        while (isActive) {
                            val result = runCatching { logController.initLogger() }
                            if (result.isSuccess) {
                                break
                            }
                        }
                        runLater {
                            listView.placeholder = emptyPlaceholder
                        }
                    }

                    GuiEventBus.ChangingSession -> runLater {
                        items.clear()
                        followTail.set(true)
                        listView.placeholder = loadingPlaceholder
                    }

                    is GuiEventBus.RemoteError -> runLater {
                        items.clear()
                        followTail.set(true)
                        listView.placeholder = connectionFailurePlaceholder
                    }

                    else -> {}
                }
            }
        }
    }

    private fun applyLogFilter() {
        val query = searchInput.get().orEmpty().trim()
        val threadId = threadIdInput.get().orEmpty().trim()
        val postId = postIdInput.get().orEmpty().trim()
        val filter = quickFilterProperty.get() ?: LogQuickFilter.ALL
        emptyMessage.set(
            if (query.isBlank() && threadId.isBlank() && postId.isBlank() && filter == LogQuickFilter.ALL) {
                "No logs yet"
            } else {
                "No matching logs"
            }
        )
        filteredItems.predicate = Predicate { log ->
            matchesQuery(log, query) &&
                    matchesQuickFilter(log, filter) &&
                    matchesId(log.threadId, threadId) &&
                    matchesId(log.postId, postId)
        }
        if (followTail.get()) {
            scrollToBottom()
        }
    }

    private fun matchesQuery(log: LogModel, query: String): Boolean =
        query.isBlank() ||
                log.formattedMessage.contains(query, ignoreCase = true) ||
                log.loggerName.contains(query, ignoreCase = true) ||
                log.threadName.contains(query, ignoreCase = true) ||
                log.levelString.contains(query, ignoreCase = true) ||
                log.throwable.contains(query, ignoreCase = true) ||
                log.threadId?.toString()?.contains(query, ignoreCase = true) == true ||
                log.postId?.toString()?.contains(query, ignoreCase = true) == true

    private fun matchesQuickFilter(log: LogModel, filter: LogQuickFilter): Boolean {
        val level = log.levelString.uppercase()
        return when (filter) {
            LogQuickFilter.ALL -> true
            LogQuickFilter.ERRORS -> level == "ERROR" || level == "FATAL"
            LogQuickFilter.WARNINGS -> level == "WARN" || level == "WARNING"
            LogQuickFilter.INFO -> level == "INFO"
            LogQuickFilter.DEBUG -> level == "DEBUG" || level == "TRACE"
        }
    }

    private fun matchesId(value: Long?, query: String): Boolean =
        query.isBlank() || value?.toString() == query

    private fun clearLogs() {
        confirm(
            "",
            "Are you sure you want to clear all logs",
            ButtonType.YES,
            ButtonType.NO,
            owner = primaryStage,
            title = "Clear logs"
        ) {
            items.clear()
            followTail.set(true)
            listView.placeholder = emptyPlaceholder
        }
    }

    private fun scrollToBottom() {
        if (listView.items.isNotEmpty()) {
            listView.scrollTo(listView.items.size - 1)
        }
    }

    private fun openLog(item: LogModel) {
        find<LogMessageFragment>(mapOf(LogMessageFragment::logModel to item)).openModal()?.apply {
            minWidth = 100.0
            minHeight = 100.0
        }
    }

    private fun copyLog(item: LogModel) {
        val content = ClipboardContent()
        content.putString(plainText(item))
        Clipboard.getSystemClipboard().setContent(content)
    }

    private fun plainText(item: LogModel): String = buildString {
        append(item.timestamp)
        append(' ')
        append(item.levelString)
        append(' ')
        append(item.loggerName)
        append(" [")
        append(item.threadName)
        append(']')
        item.threadId?.let { append(" [thread=").append(it).append(']') }
        item.postId?.let { append(" [post=").append(it).append(']') }
        append(' ')
        append(item.formattedMessage)
        if (item.throwable.isNotBlank()) {
            append('\n')
            append(item.throwable.trimEnd())
        }
    }
}

private class TerminalLogCell : ListCell<LogModel>() {
    private val flow = TextFlow().apply {
        lineSpacing = 2.0
        style = "-fx-font-family: monospace;"
    }

    init {
        padding = Insets(2.0, 8.0, 2.0, 8.0)
    }

    override fun computePrefHeight(width: Double): Double {
        if (width <= 0.0) {
            return super.computePrefHeight(width)
        }
        val contentWidth = (width - padding.left - padding.right).coerceAtLeast(1.0)
        flow.prefWidth = contentWidth
        flow.maxWidth = contentWidth
        return flow.prefHeight(contentWidth) + padding.top + padding.bottom
    }

    override fun updateItem(item: LogModel?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) {
            graphic = null
            text = null
        } else {
            flow.children.setAll(buildLogText(item))
            graphic = flow
        }
    }
}

private fun buildLogText(item: LogModel): List<Text> {
    val parts = mutableListOf<Text>()

    fun add(value: String, color: String? = null) {
        parts.add(Text(value).apply {
            if (color != null) {
                style = "-fx-fill: $color;"
            }
        })
    }

    add(item.timestamp, "-color-fg-muted")
    add(" ")
    add(item.levelString.padEnd(5), levelColor(item.levelString))
    add(" ")
    add(item.loggerName, "-color-fg-muted")
    add(" [${item.threadName}]", "-color-fg-muted")
    item.threadId?.let { add(" [thread=$it]", "-color-accent-fg") }
    item.postId?.let { add(" [post=$it]", "-color-accent-fg") }
    add(" ${item.formattedMessage}")
    if (item.throwable.isNotBlank()) {
        add("\n${item.throwable.trimEnd()}", "-color-danger-emphasis")
    }
    return parts
}

private fun levelColor(level: String): String = when (level.uppercase()) {
    "ERROR", "FATAL" -> "-color-danger-emphasis"
    "WARN", "WARNING" -> "-color-warning-emphasis"
    "DEBUG", "TRACE" -> "-color-fg-muted"
    else -> "-color-accent-emphasis"
}
