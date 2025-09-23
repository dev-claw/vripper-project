package me.vripper.entities

import kotlinx.serialization.Serializable

@Serializable
data class MetadataEntity(val postIdRef: Long, val data: Data) {
    @Serializable
    data class Data(
        val postedBy: String,
        val resolvedNames: List<String>
    )
}
