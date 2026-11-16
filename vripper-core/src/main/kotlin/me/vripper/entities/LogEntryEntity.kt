package me.vripper.entities

import kotlinx.serialization.Serializable

@Serializable
data class LogEntryEntity(
    val sequence: Long,
    val timestamp: String,
    val threadName: String,
    val loggerName: String,
    val levelString: String,
    val formattedMessage: String,
    val throwable: String,
    val threadId: Long? = null,
    val postId: Long? = null,
)