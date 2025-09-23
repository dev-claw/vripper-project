package me.vripper.entities

import kotlinx.serialization.Serializable

@Serializable
data class ImageEntity(
    val id: Long = -1,
    val url: String,
    val thumbUrl: String,
    val host: Byte,
    val index: Int,
    val postEntityId: Long = -1,
    var size: Long = -1,
    var downloaded: Long = 0,
    var status: Status = Status.STOPPED,
    var filename: String = "",
)