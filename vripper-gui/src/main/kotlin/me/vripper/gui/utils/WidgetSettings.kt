package me.vripper.gui.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.vripper.gui.model.WidgetsViewModel
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import java.nio.file.Files
import kotlin.io.path.pathString

object WidgetSettings {

    private val WIDGETS_CONFIG_PATH = VRIPPER_DIR.resolve("widgets-config.json")

    private val json = Json {
        prettyPrint = true
        allowSpecialFloatingPointValues = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class ThreadSelectionTableColumns(
        val preview: Boolean = true,
        val index: Boolean = true,
        val title: Boolean = true,
        val link: Boolean = true,
        val hosts: Boolean = true,
    )

    @Serializable
    data class ThreadSelectionTableColumnsWidth(
        val preview: Double = 100.0,
        val index: Double = 100.0,
        val title: Double = 200.0,
        val link: Double = 200.0,
        val hosts: Double = 100.0,
    )

    @Serializable
    data class LogsTableColumns(
        val time: Boolean = true,
        val threadName: Boolean = true,
        val loggerName: Boolean = true,
        val levelString: Boolean = true,
        val message: Boolean = true,
    )

    @Serializable
    data class LogsTableColumnsWidth(
        val time: Double = 150.0,
        val threadName: Double = 200.0,
        val loggerName: Double = 200.0,
        val levelString: Double = 200.0,
        val message: Double = 300.0,
    )

    @Serializable
    data class ThreadsTableColumns(
        val title: Boolean = true,
        val link: Boolean = true,
        val count: Boolean = true,
    )

    @Serializable
    data class ThreadsTableColumnsWidth(
        val title: Double = 350.0,
        val link: Double = 350.0,
        val count: Double = 100.0,
    )

    @Serializable
    data class PostsTableColumns(
        val preview: Boolean = true,
        val title: Boolean = true,
        val progress: Boolean = true,
        val status: Boolean = true,
        val path: Boolean = true,
        val total: Boolean = true,
        val hosts: Boolean = true,
        val addedOn: Boolean = true,
        val order: Boolean = true,
    )

    @Serializable
    data class PostsTableColumnsWidth(
        val preview: Double = 100.0,
        val title: Double = 250.0,
        val progress: Double = 100.0,
        val status: Double = 100.0,
        val path: Double = 250.0,
        val total: Double = 150.0,
        val hosts: Double = 100.0,
        val addedOn: Double = 125.0,
        val order: Double = 100.0,
    )

    @Serializable
    data class ImagesTableColumns(
        val preview: Boolean = true,
        val index: Boolean = true,
        val link: Boolean = true,
        val progress: Boolean = true,
        val filename: Boolean = true,
        val status: Boolean = true,
        val size: Boolean = true,
        val downloaded: Boolean = true,
    )

    @Serializable
    data class ImagesTableColumnsWidth(
        val preview: Double = 100.0,
        val index: Double = 100.0,
        val link: Double = 200.0,
        val progress: Double = 100.0,
        val filename: Double = 150.0,
        val status: Double = 100.0,
        val size: Double = 75.0,
        val downloaded: Double = 125.0,
    )

    @Serializable
    data class RemoteSession(
        val host: String = "",
        val port: Int = 30000,
        val passPhrase: String = "",
    )

    @Serializable
    data class WidgetsSettings(
        val localSession: Boolean = true,
        val visibleInfoPanel: Boolean = true,
        val visibleToolbarPanel: Boolean = true,
        val visibleStatusBarPanel: Boolean = true,
        val darkMode: Boolean = false,
        val theme: String = "CupertinoLight",
        val infoPanelDividerPosition: Double = 0.7,
        val width: Double = 1366.0,
        val height: Double = 768.0,
        val cachePath: String = VRIPPER_DIR.resolve("previews").pathString,
        val postsTableColumns: PostsTableColumns = PostsTableColumns(),
        val imagesTableColumns: ImagesTableColumns = ImagesTableColumns(),
        val threadsTableColumns: ThreadsTableColumns = ThreadsTableColumns(),
        val logsTableColumns: LogsTableColumns = LogsTableColumns(),
        val threadSelectionColumns: ThreadSelectionTableColumns = ThreadSelectionTableColumns(),
        val remoteSession: RemoteSession = RemoteSession(),
        val postsTableColumnsWidth: PostsTableColumnsWidth = PostsTableColumnsWidth(),
        val imagesTableColumnsWidth: ImagesTableColumnsWidth = ImagesTableColumnsWidth(),
        val threadsTableColumnsWidth: ThreadsTableColumnsWidth = ThreadsTableColumnsWidth(),
        val threadSelectionColumnsWidth: ThreadSelectionTableColumnsWidth = ThreadSelectionTableColumnsWidth(),
        val logsTableColumnsWidth: LogsTableColumnsWidth = LogsTableColumnsWidth(),
    )

    fun loadSettings(): WidgetsSettings {
        if (!Files.exists(WIDGETS_CONFIG_PATH)) {
            return WidgetsSettings()
        }
        return (json.decodeFromString(Files.readString(WIDGETS_CONFIG_PATH)) as WidgetsSettings)
    }

    @Synchronized
    fun update(currentSettings: WidgetsViewModel) {
        Files.writeString(
            WIDGETS_CONFIG_PATH, json.encodeToString(
                WidgetsSettings(
                    currentSettings.localSession,
                    currentSettings.visibleInfoPanel,
                    currentSettings.visibleToolbarPanel,
                    currentSettings.visibleStatusBarPanel,
                    currentSettings.darkMode,
                    currentSettings.theme,
                    currentSettings.infoPanelDividerPosition,
                    currentSettings.width,
                    currentSettings.height,
                    currentSettings.cachePath,
                    PostsTableColumns(
                        currentSettings.postsColumnsModel.preview,
                        currentSettings.postsColumnsModel.title,
                        currentSettings.postsColumnsModel.progress,
                        currentSettings.postsColumnsModel.status,
                        currentSettings.postsColumnsModel.path,
                        currentSettings.postsColumnsModel.total,
                        currentSettings.postsColumnsModel.hosts,
                        currentSettings.postsColumnsModel.addedOn,
                        currentSettings.postsColumnsModel.order,
                    ),
                    ImagesTableColumns(
                        currentSettings.imagesColumnsModel.preview,
                        currentSettings.imagesColumnsModel.index,
                        currentSettings.imagesColumnsModel.link,
                        currentSettings.imagesColumnsModel.progress,
                        currentSettings.imagesColumnsModel.filename,
                        currentSettings.imagesColumnsModel.status,
                        currentSettings.imagesColumnsModel.size,
                        currentSettings.imagesColumnsModel.downloaded,
                    ),
                    ThreadsTableColumns(
                        currentSettings.threadsColumnsModel.title,
                        currentSettings.threadsColumnsModel.link,
                        currentSettings.threadsColumnsModel.count,
                    ),
                    LogsTableColumns(
                        currentSettings.logsColumnsModel.time,
                        currentSettings.logsColumnsModel.threadName,
                        currentSettings.logsColumnsModel.loggerName,
                        currentSettings.logsColumnsModel.levelString,
                        currentSettings.logsColumnsModel.message,
                    ),
                    ThreadSelectionTableColumns(
                        currentSettings.threadSelectionColumnsModel.preview,
                        currentSettings.threadSelectionColumnsModel.index,
                        currentSettings.threadSelectionColumnsModel.title,
                        currentSettings.threadSelectionColumnsModel.link,
                        currentSettings.threadSelectionColumnsModel.hosts,
                    ),
                    RemoteSession(
                        currentSettings.remoteSessionModel.host,
                        currentSettings.remoteSessionModel.port,
                        currentSettings.remoteSessionModel.passcode
                    ),
                    PostsTableColumnsWidth(
                        currentSettings.postsColumnsWidthModel.preview,
                        currentSettings.postsColumnsWidthModel.title,
                        currentSettings.postsColumnsWidthModel.progress,
                        currentSettings.postsColumnsWidthModel.status,
                        currentSettings.postsColumnsWidthModel.path,
                        currentSettings.postsColumnsWidthModel.total,
                        currentSettings.postsColumnsWidthModel.hosts,
                        currentSettings.postsColumnsWidthModel.addedOn,
                        currentSettings.postsColumnsWidthModel.order,
                    ),
                    ImagesTableColumnsWidth(
                        currentSettings.imagesColumnsWidthModel.preview,
                        currentSettings.imagesColumnsWidthModel.index,
                        currentSettings.imagesColumnsWidthModel.link,
                        currentSettings.imagesColumnsWidthModel.progress,
                        currentSettings.imagesColumnsWidthModel.filename,
                        currentSettings.imagesColumnsWidthModel.status,
                        currentSettings.imagesColumnsWidthModel.size,
                        currentSettings.imagesColumnsWidthModel.downloaded,
                    ),
                    ThreadsTableColumnsWidth(
                        currentSettings.threadsColumnsWidthModel.title,
                        currentSettings.threadsColumnsWidthModel.link,
                        currentSettings.threadsColumnsWidthModel.count,
                    ),
                    ThreadSelectionTableColumnsWidth(
                        currentSettings.threadSelectionColumnsWidthModel.preview,
                        currentSettings.threadSelectionColumnsWidthModel.index,
                        currentSettings.threadSelectionColumnsWidthModel.title,
                        currentSettings.threadSelectionColumnsWidthModel.link,
                        currentSettings.threadSelectionColumnsWidthModel.hosts,
                    ),
                    LogsTableColumnsWidth(
                        currentSettings.logsColumnsWidthModel.time,
                        currentSettings.logsColumnsWidthModel.threadName,
                        currentSettings.logsColumnsWidthModel.loggerName,
                        currentSettings.logsColumnsWidthModel.levelString,
                        currentSettings.logsColumnsWidthModel.message,
                    ),
                )
            )
        )
    }

}