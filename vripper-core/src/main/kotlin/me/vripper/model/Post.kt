package me.vripper.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.vripper.entities.Status
import java.time.LocalDateTime
import kotlin.io.path.Path
import kotlin.io.path.pathString

@Serializable
data class Post(
    val id: Long = -1,
    val postTitle: String,
    val threadTitle: String,
    val forum: String,
    val url: String,
    val token: String,
    val postId: Long,
    val threadId: Long,
    val total: Int,
    val hosts: Set<String>,
    val downloadDirectory: String,
    @Contextual val addedOn: LocalDateTime,
    var folderName: String,
    var status: Status = Status.STOPPED,
    var done: Int = 0,
    var size: Long = -1,
    var downloaded: Long = 0,
    val previews: List<String>,
    val postedBy: String,
    val resolvedNames: List<String>
) {
    fun getDownloadFolder() = Path(downloadDirectory, folderName).pathString
}
