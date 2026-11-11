package me.vripper.model

data class PostSelection(
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
    val author: String = "",
    val postDate: String = "",
    val edited: String = "",
)
