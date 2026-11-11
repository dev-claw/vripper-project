package me.vripper.vgapi

internal data class PostItem(
    val threadId: Long,
    val threadTitle: String,
    val postId: Long,
    val number: Int,
    val title: String,
    val imageCount: Int,
    val url: String,
    val hosts: List<Pair<String, Int>>,
    val securityToken: String,
    val forum: String,
    val imageItemList: List<ImageItem>,
    val prefixId: String = "",
    val author: String = "",
    val userId: Long = -1,
    val date: String = "",
    val edited: String = "",
)
