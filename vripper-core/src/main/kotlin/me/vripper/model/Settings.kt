package me.vripper.model

import kotlinx.serialization.Serializable
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import java.nio.file.Files
import kotlin.io.path.pathString

@Serializable
data class Settings(
    val connectionSettings: ConnectionSettings = ConnectionSettings(),
    val downloadSettings: DownloadSettings = DownloadSettings(),
    val viperSettings: ViperSettings = ViperSettings(),
    val systemSettings: SystemSettings = SystemSettings()
)

@Serializable
data class ViperSettings(
    val login: Boolean = false,
    val username: String = "",
    val password: String = "",
    val thanks: Boolean = false,
    val host: String = "https://vipergirls.to",
    val requestLimit: Long = 2,
    val fetchMetadata: Boolean = false,
)

@Serializable
data class DownloadSettings(
    val downloadPath: String = VRIPPER_DIR.resolve("downloads").also { Files.createDirectories(it) }.pathString,
    val autoStart: Boolean = true,
    val autoQueueThreshold: Int = 1,
    val forceOrder: Boolean = false,
    val forumSubDirectory: Boolean = false,
    val threadSubLocation: Boolean = false,
    val clearCompleted: Boolean = false,
    val appendPostId: Boolean = false
)

@Serializable
data class ConnectionSettings(
    val maxConcurrentPerHost: Int = 2,
    val maxGlobalConcurrent: Int = 0,
    val timeout: Int = 30,
    val maxAttempts: Int = 3,
)

@Serializable
data class SystemSettings(
    val tempPath: String = VRIPPER_DIR.resolve("temp").also { Files.createDirectories(it) }.pathString,
    val enableClipboardMonitoring: Boolean = false,
    val clipboardPollingRate: Int = 500,
    val maxEventLog: Int = 1_000,
)
