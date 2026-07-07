package me.vripper.data

import me.vripper.proto.ConnectionSettings as ProtoConnectionSettings
import me.vripper.proto.DownloadSettings as ProtoDownloadSettings
import me.vripper.proto.HostSettingsMap as ProtoHostSettingsMap
import me.vripper.proto.Image as ProtoImage
import me.vripper.proto.ImageChunk as ProtoImageChunk
import me.vripper.proto.Log as ProtoLog
import me.vripper.proto.Metadata as ProtoMetadata
import me.vripper.proto.Post as ProtoPost
import me.vripper.proto.PostSelection as ProtoPostSelection
import me.vripper.proto.QueueState as ProtoQueueState
import me.vripper.proto.Settings as ProtoSettings
import me.vripper.proto.SystemSettings as ProtoSystemSettings
import me.vripper.proto.Task as ProtoTask
import me.vripper.proto.Thread as ProtoThread
import me.vripper.proto.ViperSettings as ProtoViperSettings

data class Post(
    val id: Long,
    val postId: Long,
    val threadId: Long,
    val title: String,
    val threadTitle: String,
    val forum: String,
    val url: String,
    val status: String,
    val done: Int,
    val total: Int,
    val size: Long,
    val downloaded: Long,
    val hosts: List<String>,
    val addedOn: String,
    val folderName: String,
    val downloadDirectory: String,
    val previews: List<String>,
    val postedBy: String,
    val resolvedNames: List<String>,
) {
    val progress: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    val isFinished: Boolean
        get() = status.equals("FINISHED", ignoreCase = true)

    val isRunning: Boolean
        get() = status.equals("RUNNING", ignoreCase = true)
}

data class PostMetadata(
    val postId: Long,
    val postedBy: String,
    val resolvedNames: List<String>,
)

data class QueueState(
    val running: Int = 0,
    val remaining: Int = 0,
    val ranks: Map<Long, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = running == 0 && remaining == 0
}

data class Image(
    val id: Long,
    val url: String,
    val thumbUrl: String,
    val host: Int,
    val index: Int,
    val postId: Long,
    val size: Long,
    val downloaded: Long,
    val status: String,
    val filename: String,
) {
    val progress: Float
        get() = if (size <= 0) 0f else (downloaded.toFloat() / size).coerceIn(0f, 1f)

    val displayName: String
        get() = filename.ifBlank {
            url.substringAfterLast('/').substringBefore('?').ifBlank { "Image ${index + 1}" }
        }
}

class RemoteImageChunk(
    val missing: Boolean,
    val offset: Long,
    val data: ByteArray,
    val isLast: Boolean,
)

data class ForumThread(
    val id: Long,
    val title: String,
    val link: String,
    val threadId: Long,
    val total: Int,
)

data class ThreadSelection(
    val threadId: Long,
    val threadTitle: String,
    val postId: Long,
    val number: Int,
    val title: String,
    val imageCount: Int,
    val url: String,
    val hosts: String,
    val forum: String,
    val previews: List<String>,
)

data class ThreadPostRef(
    val threadId: Long,
    val postId: Long,
)

data class DownloadTask(
    val id: Long,
    val name: String,
    val category: String,
    val description: String,
    val status: String,
    val result: String,
    val createdAt: String,
    val updatedAt: String,
)

data class LogEntry(
    val uid: Long = 0,
    val sequence: Long,
    val timestamp: String,
    val threadName: String,
    val loggerName: String,
    val level: String,
    val message: String,
    val throwable: String,
)

data class ViperSettings(
    val login: Boolean = false,
    val username: String = "",
    val password: String = "",
    val thanks: Boolean = false,
    val host: String = "",
    val requestLimit: Long = 0,
    val fetchMetadata: Boolean = false,
)

data class DownloadSettings(
    val downloadPath: String = "",
    val autoStart: Boolean = false,
    val autoQueueThreshold: Int = 0,
    val forceOrder: Boolean = false,
    val forumSubDirectory: Boolean = false,
    val threadSubLocation: Boolean = false,
    val clearCompleted: Boolean = false,
    val appendPostId: Boolean = false,
)

data class ConnectionSettings(
    val maxConcurrentPerHost: Int = 0,
    val maxGlobalConcurrent: Int = 0,
    val timeout: Int = 0,
    val maxAttempts: Int = 0,
)

data class SystemSettings(
    val tempPath: String = "",
    val enableClipboardMonitoring: Boolean = false,
    val clipboardPollingRate: Int = 0,
    val maxEventLog: Int = 1000,
)

data class Settings(
    val viper: ViperSettings = ViperSettings(),
    val download: DownloadSettings = DownloadSettings(),
    val connection: ConnectionSettings = ConnectionSettings(),
    val system: SystemSettings = SystemSettings(),
    val hostSettings: Map<String, Map<String, String>> = emptyMap(),
)

enum class MovePosition {
    UP,
    DOWN,
    TOP,
    BOTTOM,
}

fun ProtoPost.toDomain(): Post = Post(
    id = id,
    postId = postId,
    threadId = threadId,
    title = postTitle,
    threadTitle = threadTitle,
    forum = forum,
    url = url,
    status = status,
    done = done,
    total = total,
    size = size,
    downloaded = downloaded,
    hosts = hosts,
    addedOn = addedOn,
    folderName = folderName,
    downloadDirectory = downloadDirectory,
    previews = previews,
    postedBy = postedBy,
    resolvedNames = resolvedNames,
)

fun ProtoQueueState.toDomain(): QueueState = QueueState(
    running = running,
    remaining = remaining,
    ranks = rank.associate { it.postEntityId to it.rank },
)

fun ProtoMetadata.toDomain(): PostMetadata = PostMetadata(
    postId = postId,
    postedBy = postedBy,
    resolvedNames = resolvedNames,
)

fun ProtoImage.toDomain(): Image = Image(
    id = id,
    url = url,
    thumbUrl = thumbUrl,
    host = host,
    index = index,
    postId = postIdRef,
    size = size,
    downloaded = downloaded,
    status = status,
    filename = filename,
)

fun ProtoImageChunk.toDomain(): RemoteImageChunk = RemoteImageChunk(
    missing = missing,
    offset = offset,
    data = data_.toByteArray(),
    isLast = isLast,
)

fun ProtoThread.toDomain(): ForumThread = ForumThread(
    id = id,
    title = title,
    link = link,
    threadId = threadId,
    total = total,
)

fun ProtoPostSelection.toDomain(): ThreadSelection = ThreadSelection(
    threadId = threadId,
    threadTitle = threadTitle,
    postId = postId,
    number = number,
    title = title,
    imageCount = imageCount,
    url = url,
    hosts = hosts,
    forum = forum,
    previews = previews,
)

fun ProtoTask.toDomain(): DownloadTask = DownloadTask(
    id = id,
    name = name,
    category = category,
    description = description,
    status = status,
    result = result,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProtoLog.toDomain(): LogEntry = LogEntry(
    sequence = sequence,
    timestamp = timestamp,
    threadName = threadName,
    loggerName = loggerName,
    level = levelString,
    message = formattedMessage,
    throwable = throwable,
)

fun ProtoSettings.toDomain(): Settings = Settings(
    viper = viperSettings?.toDomain() ?: ViperSettings(),
    download = downloadSettings?.toDomain() ?: DownloadSettings(),
    connection = connectionSettings?.toDomain() ?: ConnectionSettings(),
    system = systemSettings?.toDomain() ?: SystemSettings(),
    hostSettings = hostSettings.mapValues { (_, value) -> value.settings },
)

fun ProtoViperSettings.toDomain(): ViperSettings = ViperSettings(
    login = login,
    username = username,
    password = password,
    thanks = thanks,
    host = host,
    requestLimit = requestLimit,
    fetchMetadata = fetchMetadata,
)

fun ProtoDownloadSettings.toDomain(): DownloadSettings = DownloadSettings(
    downloadPath = downloadPath,
    autoStart = autoStart,
    autoQueueThreshold = autoQueueThreshold,
    forceOrder = forceOrder,
    forumSubDirectory = forumSubDirectory,
    threadSubLocation = threadSubLocation,
    clearCompleted = clearCompleted,
    appendPostId = appendPostId,
)

fun ProtoConnectionSettings.toDomain(): ConnectionSettings = ConnectionSettings(
    maxConcurrentPerHost = maxConcurrentPerHost,
    maxGlobalConcurrent = maxGlobalConcurrent,
    timeout = timeout,
    maxAttempts = maxAttempts,
)

fun ProtoSystemSettings.toDomain(): SystemSettings = SystemSettings(
    tempPath = tempPath,
    enableClipboardMonitoring = enableClipboardMonitoring,
    clipboardPollingRate = clipboardPollingRate,
    maxEventLog = maxEventLog,
)

fun Settings.toProto(): ProtoSettings = ProtoSettings(
    viperSettings = viper.toProto(),
    downloadSettings = download.toProto(),
    connectionSettings = connection.toProto(),
    systemSettings = system.toProto(),
    hostSettings = hostSettings.mapValues { (_, values) ->
        ProtoHostSettingsMap(settings = values)
    },
)

fun ViperSettings.toProto(): ProtoViperSettings = ProtoViperSettings(
    login = login,
    username = username,
    password = password,
    thanks = thanks,
    host = host,
    requestLimit = requestLimit,
    fetchMetadata = fetchMetadata,
)

fun DownloadSettings.toProto(): ProtoDownloadSettings = ProtoDownloadSettings(
    downloadPath = downloadPath,
    autoStart = autoStart,
    autoQueueThreshold = autoQueueThreshold,
    forceOrder = forceOrder,
    forumSubDirectory = forumSubDirectory,
    threadSubLocation = threadSubLocation,
    clearCompleted = clearCompleted,
    appendPostId = appendPostId,
)

fun ConnectionSettings.toProto(): ProtoConnectionSettings = ProtoConnectionSettings(
    maxConcurrentPerHost = maxConcurrentPerHost,
    maxGlobalConcurrent = maxGlobalConcurrent,
    timeout = timeout,
    maxAttempts = maxAttempts,
)

fun SystemSettings.toProto(): ProtoSystemSettings = ProtoSystemSettings(
    tempPath = tempPath,
    enableClipboardMonitoring = enableClipboardMonitoring,
    clipboardPollingRate = clipboardPollingRate,
    maxEventLog = maxEventLog,
)
