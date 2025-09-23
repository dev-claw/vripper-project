package me.vripper.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueState(
    val running: Int,
    val remaining: Int,
    val rank: List<Rank> = emptyList()
)
