package me.vripper.entities

import kotlinx.serialization.Serializable

@Serializable
data class ThreadEntity(
    val id: Long = -1,
    val title: String,
    val link: String,
    val threadId: Long,
    var total: Int = 0,
)