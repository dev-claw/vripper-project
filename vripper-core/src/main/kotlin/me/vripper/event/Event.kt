package me.vripper.event

import me.vripper.entities.*
import me.vripper.model.DownloadSpeed
import me.vripper.model.ErrorCount
import me.vripper.model.QueueState
import me.vripper.model.Settings

data class PostCreateEvent(val postEntities: List<PostEntity>)
data class PostUpdateEvent(val postEntities: List<PostEntity>)
data class PostDeleteEvent(val postEntityIds: List<Long>)
data class ImageEvent(val imageEntities: List<ImageEntity>)
data class ThreadCreateEvent(val threadEntity: ThreadEntity)
data class ThreadUpdateEvent(val threadEntity: ThreadEntity)
data class ThreadDeleteEvent(val threadId: Long)
class ThreadClearEvent
data class VGUserLoginEvent(val username: String)
data class DownloadSpeedEvent(val downloadSpeed: DownloadSpeed)
data class QueueStateEvent(val queueState: QueueState)
data class ErrorCountEvent(val errorCount: ErrorCount)
data class SettingsUpdateEvent(val settings: Settings)
data class MetadataUpdateEvent(val metadataEntity: MetadataEntity)
data class LogCreateEvent(val logEntryEntity: LogEntryEntity)
data class LoadingTasks(val loading: Boolean)
data class StoppedEvent(val postIds: List<Long>)