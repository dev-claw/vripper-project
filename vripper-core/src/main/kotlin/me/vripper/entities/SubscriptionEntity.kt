package me.vripper.entities

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionEntity(
    val id: Long = -1,
    val title: String,
    val link: String,
    val threadId: Long,
    var total: Int = 0,
    val forumId: Long = -1,
    val forum: String = "",
    val prefixId: String = "",
    val starter: String = "",
    val starterId: Long = -1,
    val created: String = "",
    val lastPost: String = "",
    val lastPostId: Long = -1,
    val lastPoster: String = "",
    val replyCount: Int = 0,
    val views: Int = 0,
    val open: Boolean = true,
    val sticky: Boolean = false,
    val newestPostId: Long = 0,
)
