package me.vripper.entities

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class PostEntity(
    val id: Long = -1,
    val postTitle: String,
    val threadTitle: String,
    val forum: String,
    val url: String,
    val token: String,
    val vgPostId: Long,
    val vgThreadId: Long,
    val total: Int,
    val hosts: Set<String>,
    val downloadDirectory: String,
    @Contextual val addedOn: LocalDateTime = LocalDateTime.now(),
    var folderName: String,
    var status: Status = Status.STOPPED,
    var done: Int = 0,
    var size: Long = -1,
    var downloaded: Long = 0,
)