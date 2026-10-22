package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TitledPane
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import kotlinx.coroutines.*
import me.vripper.gui.controller.PostController
import me.vripper.gui.controller.StatusBarController
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GUIEvent
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.gui.utils.ImageCacheManager
import me.vripper.gui.utils.openLink
import me.vripper.utilities.ApplicationProperties
import me.vripper.utilities.formatSI
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.pathString

/**
 * Read-only system page: endpoint status, queue, application paths,
 * environment information, JVM health and cache usage. In remote mode the
 * server's paths, environment and JVM are shown separately from the client's
 * own cache and log information. Values are refreshed periodically while the
 * window is open and can be copied as plain text.
 */
class DiagnosticsFragment : Fragment("Diagnostics") {

    private val widgetsController: WidgetsController by inject()
    private val postController: PostController by inject()
    private val statusBarController: StatusBarController by inject()

    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null
    private var autoRefreshJob: Job? = null

    private val updatedLabel = Label("Loading…").apply {
        addClass(Styles.TEXT_MUTED)
    }

    private val sectionsContainer = vbox(12.0) {
        padding = Insets(12.0, 0.0, 12.0, 0.0)
    }
    private var sections: Map<String, List<String>> = emptyMap()

    // Values are keyed by row name so refreshing, copying and section building
    // all share one definition.
    private val fields = LinkedHashMap<String, Label>()

    override val root = borderpane {
        padding = Insets(14.0)
        top = hbox(8.0) {
            alignment = Pos.CENTER_LEFT
            label("System diagnostics") {
                addClass(Styles.TITLE_3)
            }
            spacer()
            button("Refresh") {
                graphic = FontIcon.of(Feather.REFRESH_CW)
                action { refresh() }
            }
            button("Run GC") {
                tooltip("Runs garbage collection in this application")
                action {
                    updatedLabel.text = "Running garbage collection…"
                    diagnosticsScope.launch {
                        System.gc()
                        runLater { if (isDocked) refresh() }
                    }
                }
            }
            button("Copy") {
                graphic = FontIcon.of(Feather.CLIPBOARD)
                action { copyToClipboard() }
            }
            button("Open data folder") {
                graphic = FontIcon.of(Feather.FOLDER)
                action { openDataFolder() }
            }
        }
        center = scrollpane {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            style = "-fx-background-color: transparent;"
            content = sectionsContainer
        }
        bottom = hbox {
            padding = Insets(8.0, 0.0, 0.0, 0.0)
            add(updatedLabel)
        }
    }

    override fun onBeforeShow() {
        super.onBeforeShow()
        // Size the stage before TornadoFX centers it on the owner. Resizing
        // after openModal() returns leaves the original top-left in place.
        currentStage?.apply {
            minWidth = 600.0
            minHeight = 400.0
            width = 900.0
            height = 680.0
        }
    }

    override fun onDock() {
        super.onDock()
        centerOnOwner()
        configureSections(AppEndpointManager.currentAppState() == GuiEventBus.RemoteSession)
        autoRefreshJob?.cancel()
        autoRefreshJob = diagnosticsScope.launch {
            while (isActive) {
                runLater { if (isDocked) refresh() }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onUndock() {
        autoRefreshJob?.cancel()
        refreshJob?.cancel()
        diagnosticsScope.cancel()
        super.onUndock()
    }

    /**
     * TornadoFX centers on the scene (content) size; use the stage size so the
     * whole window is centered on the owner, decorations included.
     */
    private fun centerOnOwner() {
        currentStage?.let { stage ->
            val owner = stage.owner
            if (owner == null) {
                stage.centerOnScreen()
            } else {
                stage.x = owner.x + (owner.width - stage.width) / 2
                stage.y = owner.y + (owner.height - stage.height) / 2
            }
        }
    }

    private fun configureSections(remote: Boolean) {
        val wanted = if (remote) REMOTE_SECTIONS else LOCAL_SECTIONS
        if (wanted == sections && fields.isNotEmpty()) return
        sections = wanted
        fields.clear()
        sectionsContainer.children.clear()
        wanted.forEach { (title, names) ->
            sectionsContainer.add(buildSection(title, names))
        }
    }

    private fun buildSection(title: String, names: List<String>): TitledPane {
        val grid = GridPane().apply {
            padding = Insets(10.0)
            hgap = 18.0
            vgap = 7.0
            columnConstraints.addAll(
                ColumnConstraints().apply { minWidth = Region.USE_PREF_SIZE },
                ColumnConstraints().apply {
                    minWidth = 0.0
                    hgrow = Priority.ALWAYS
                },
            )
        }
        names.forEachIndexed { row, name ->
            val value = value(name)
            grid.add(
                Label(name).apply {
                    addClass(Styles.TEXT_MUTED)
                    minWidth = Region.USE_PREF_SIZE
                },
                0,
                row,
            )
            grid.add(value, 1, row)
            GridPane.setHgrow(value, Priority.ALWAYS)
            GridPane.setFillWidth(value, true)
        }
        return TitledPane(title, grid).apply {
            isCollapsible = false
            isExpanded = true
            maxWidth = Double.MAX_VALUE
        }
    }

    private fun value(name: String): Label = fields.getOrPut(name) {
        Label().apply {
            isWrapText = true
            minWidth = 0.0
            maxWidth = Double.MAX_VALUE
        }
    }

    private fun refresh() {
        if (!diagnosticsScope.isActive) return
        val state = captureUiState()
        refreshJob?.cancel()
        refreshJob = diagnosticsScope.launch {
            val snapshot = collect(state)
            runLater {
                if (isDocked) apply(snapshot)
            }
        }
    }

    private fun captureUiState(): UiState {
        val settings = widgetsController.currentSettings
        val remote = settings.remoteSessionModel
        return UiState(
            session = AppEndpointManager.currentAppState(),
            remoteHost = remote.host,
            remotePort = remote.port,
            workspaces = PostWorkspaceFragment.cacheStats(),
        )
    }

    private suspend fun collect(state: UiState): Snapshot {
        val remote = state.session == GuiEventBus.RemoteSession
        val connectionState = if (remote) {
            runCatching { statusBarController.connectionState() }.getOrNull().orEmpty()
        } else {
            ""
        }
        val queue = runCatching { postController.getQueueState() }.getOrNull()
        val user = runCatching { statusBarController.loggedInUser() }.getOrNull().orEmpty()
        val serverInfo = if (remote) {
            runCatching { AppEndpointManager.currentAppEndpointService().getServerInfo() }.getOrNull()
        } else {
            null
        }

        val clientDir = ApplicationProperties.VRIPPER_DIR
        val clientLog = clientDir.resolve("vripper.log")
        val widgetSettingsFile = clientDir.resolve("widgets-config.json")
        val cacheDir = clientDir.resolve("cache")
        val database = clientDir.resolve("vripper.db")
        val settingsFile = clientDir.resolve("config.json")
        val dbExists = runCatching { Files.isRegularFile(database) }.getOrDefault(false)

        val sessionReady = when (state.session) {
            GuiEventBus.LocalSession -> true
            GuiEventBus.RemoteSession -> connectionState == "READY"
            else -> false
        }
        val healthy: Boolean
        val overall: String
        if (remote) {
            healthy = sessionReady && serverInfo != null && serverInfo.databaseExists
            overall = when {
                !sessionReady -> "Degraded — endpoint not ready"
                serverInfo == null -> "Degraded — server information unavailable"
                !serverInfo.databaseExists -> "Degraded — server database not found"
                else -> "OK"
            }
        } else {
            healthy = dbExists && sessionReady
            overall = when {
                !dbExists -> "Degraded — database file not found"
                !sessionReady -> "Degraded — endpoint not ready"
                else -> "OK"
            }
        }
        val session = when (state.session) {
            GuiEventBus.LocalSession -> "Local"
            GuiEventBus.RemoteSession -> buildString {
                append("Remote ${state.remoteHost}:${state.remotePort}")
                if (connectionState.isNotBlank()) append(" • $connectionState")
            }

            else -> "Not initialized"
        }
        val queueText = queue?.let { "${it.running} running • ${it.remaining} remaining" }
            ?: "Unavailable"

        val disk = ImageCacheManager.stats()
        val workspaces = state.workspaces
        val decoded = if (workspaces.windows == 0) {
            "No post windows open"
        } else {
            "${workspaces.decodedFrames} frames • ${workspaces.decodedBytes.formatSI()} / " +
                    "${workspaces.decodedLimitBytes.formatSI()} (${workspaces.windows} window(s))"
        }
        val thumbnails = if (workspaces.windows == 0) {
            "No post windows open"
        } else {
            "${workspaces.thumbnailCount} images • ${workspaces.thumbnailBytes.formatSI()}"
        }
        val runtime = Runtime.getRuntime()
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        val gcCount = gcBeans.sumOf { it.collectionCount.coerceAtLeast(0) }
        val gcTime = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }

        val values = linkedMapOf(
            "Overall" to overall,
            "Session" to session,
            "Queue" to queueText,
            "User" to user.ifBlank { "—" },
            "Open post windows" to workspaces.windows.toString(),
        )
        if (remote) {
            values["Version"] = serverInfo?.version ?: "Unavailable"
            values["Application folder"] = serverInfo?.applicationFolder ?: "Unavailable"
            values["Database"] = serverFileDescription(
                serverInfo?.databasePath,
                serverInfo?.databaseExists,
                serverInfo?.databaseSize,
            )
            values["Log file"] = serverFileDescription(
                serverInfo?.logPath,
                serverInfo?.logExists,
                serverInfo?.logSize,
            )
            values["Settings"] = serverFileDescription(
                serverInfo?.settingsPath,
                serverInfo?.settingsExists,
                serverInfo?.settingsSize,
            )
            values["Download folder"] = configuredPath(serverInfo?.downloadPath)
            values["Temp folder"] = configuredPath(serverInfo?.tempPath)
            values["Operating system"] = serverInfo?.operatingSystem ?: "Unavailable"
            values["Java"] = serverInfo?.let { "${it.javaVersion} (${it.javaVendor})" }
                ?: "Unavailable"
            values["Uptime"] = serverInfo?.let { formatUptime(it.uptimeMillis) } ?: "Unavailable"
            values["Threads"] = serverInfo?.threadCount?.toString() ?: "Unavailable"
            values["Heap memory"] = serverInfo?.let {
                memoryDescription(it.heapUsed, it.heapCommitted, it.heapMax)
            } ?: "Unavailable"
            values["Processors"] = serverInfo?.processors?.toString() ?: "Unavailable"
            values["Garbage collections"] = serverInfo?.let {
                gcDescription(it.gcCount, it.gcTimeMillis)
            } ?: "Unavailable"
            values["Widget settings"] = fileDescription(widgetSettingsFile)
            values["Client log"] = fileDescription(clientLog)
            values["Client heap memory"] = memoryDescription(
                runtime.totalMemory() - runtime.freeMemory(),
                runtime.totalMemory(),
                runtime.maxMemory(),
            )
            values["Client garbage collections"] = gcDescription(gcCount, gcTime)
        } else {
            values["Version"] = ApplicationProperties.VERSION.trim()
            values["Application folder"] = clientDir.pathString
            values["Database"] = fileDescription(database, dbExists)
            values["Log file"] = fileDescription(clientLog)
            values["Settings"] = fileDescription(settingsFile)
            values["Widget settings"] = fileDescription(widgetSettingsFile)
            values["Operating system"] = localOperatingSystem()
            values["Java"] = localJava()
            values["JavaFX"] = localJavaFx()
            values["Uptime"] = formatUptime(ManagementFactory.getRuntimeMXBean().uptime)
            values["Threads"] = ManagementFactory.getThreadMXBean().threadCount.toString()
            values["Heap memory"] = memoryDescription(
                runtime.totalMemory() - runtime.freeMemory(),
                runtime.totalMemory(),
                runtime.maxMemory(),
            )
            values["Processors"] = runtime.availableProcessors().toString()
            values["Garbage collections"] = gcDescription(gcCount, gcTime)
        }
        values["Cache folder"] = cacheDir.pathString
        values["Disk cache"] = "${disk.entries} entries • ${disk.sizeBytes.formatSI()} / " +
                disk.limitBytes.formatSI()
        values["Decoded image cache"] = decoded
        values["Thumbnails"] = thumbnails
        return Snapshot(values, healthy, remote)
    }

    private fun apply(snapshot: Snapshot) {
        configureSections(snapshot.remote)
        snapshot.values.forEach { (name, text) -> fields[name]?.text = text }
        fields["Overall"]?.styleClass?.removeAll(listOf(Styles.SUCCESS, Styles.DANGER))
        fields["Overall"]?.styleClass?.add(if (snapshot.healthy) Styles.SUCCESS else Styles.DANGER)
        updatedLabel.text = "Updated ${LocalTime.now().format(TIME_FORMAT)} • refreshing every 2s"
    }

    private fun copyToClipboard() {
        val text = buildString {
            appendLine("VRipper ${ApplicationProperties.VERSION.trim()} diagnostics")
            appendLine("Generated ${LocalTime.now().format(TIME_FORMAT)}")
            sections.forEach { (section, names) ->
                appendLine()
                appendLine("== $section ==")
                names.forEach { name ->
                    appendLine("$name: ${fields[name]?.text.orEmpty()}")
                }
            }
        }
        val content = ClipboardContent().apply { putString(text) }
        Clipboard.getSystemClipboard().setContent(content)
        updatedLabel.text = "Diagnostics copied to the clipboard"
    }

    private fun openDataFolder() {
        runCatching { openLink(ApplicationProperties.VRIPPER_DIR.toUri().toString()) }
    }

    private fun fileDescription(path: Path, knownExists: Boolean? = null): String {
        val exists = knownExists ?: runCatching { Files.isRegularFile(path) }.getOrDefault(false)
        val size = if (exists) {
            runCatching { Files.size(path) }.getOrNull()
        } else {
            null
        }
        return if (size == null) "${path.pathString} (missing)" else "${path.pathString} (${size.formatSI()})"
    }

    private fun serverFileDescription(path: String?, exists: Boolean?, size: Long?): String = when {
        path.isNullOrBlank() -> "Unavailable"
        exists != true -> "$path (missing)"
        else -> "$path (${(size ?: 0L).formatSI()})"
    }

    private fun configuredPath(path: String?): String = when {
        path == null -> "Unavailable"
        path.isBlank() -> "Not configured"
        else -> path
    }

    private fun memoryDescription(used: Long, committed: Long, max: Long): String {
        val percent = if (max in 1 until Long.MAX_VALUE) used * 100 / max else 0
        val maxText = if (max == Long.MAX_VALUE) "unlimited" else max.formatSI()
        return "${used.formatSI()} / ${committed.formatSI()} / $maxText ($percent%)"
    }

    private fun gcDescription(count: Long, timeMillis: Long): String =
        "$count collections • ${timeMillis} ms"

    private fun localOperatingSystem(): String = "${System.getProperty("os.name")} " +
            "${System.getProperty("os.version")} (${System.getProperty("os.arch")})"

    private fun localJava(): String = "${System.getProperty("java.version")} " +
            "(${System.getProperty("java.vendor")})"

    private fun localJavaFx(): String = System.getProperty("javafx.runtime.version")
        ?: System.getProperty("javafx.version")
        ?: "unknown"

    private fun formatUptime(millis: Long): String {
        val totalMinutes = millis / 60_000
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (days > 0 || hours > 0) append("${hours}h ")
            append("${minutes}m")
        }
    }

    private data class UiState(
        val session: GUIEvent,
        val remoteHost: String,
        val remotePort: Int,
        val workspaces: WorkspaceCacheStats,
    )

    private data class Snapshot(
        val values: Map<String, String>,
        val healthy: Boolean,
        val remote: Boolean,
    )

    private companion object {
        private const val REFRESH_INTERVAL_MS = 2_000L
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        private val LOCAL_SECTIONS: Map<String, List<String>> = linkedMapOf(
            "Status" to listOf(
                "Overall",
                "Session",
                "Queue",
                "User",
                "Open post windows",
            ),
            "Application" to listOf(
                "Version",
                "Application folder",
                "Database",
                "Log file",
                "Settings",
                "Widget settings",
            ),
            "Environment" to listOf(
                "Operating system",
                "Java",
                "JavaFX",
                "Uptime",
                "Threads",
                "Heap memory",
                "Processors",
                "Garbage collections",
            ),
            "Caches" to listOf(
                "Cache folder",
                "Disk cache",
                "Decoded image cache",
                "Thumbnails",
            ),
        )

        private val REMOTE_SECTIONS: Map<String, List<String>> = linkedMapOf(
            "Status" to listOf(
                "Overall",
                "Session",
                "Queue",
                "User",
                "Open post windows",
            ),
            "Server" to listOf(
                "Version",
                "Application folder",
                "Database",
                "Log file",
                "Settings",
                "Download folder",
                "Temp folder",
                "Operating system",
                "Java",
                "Uptime",
                "Threads",
                "Heap memory",
                "Processors",
                "Garbage collections",
            ),
            "This client" to listOf(
                "Widget settings",
                "Client log",
                "Client heap memory",
                "Client garbage collections",
            ),
            "Client caches" to listOf(
                "Cache folder",
                "Disk cache",
                "Decoded image cache",
                "Thumbnails",
            ),
        )
    }
}
