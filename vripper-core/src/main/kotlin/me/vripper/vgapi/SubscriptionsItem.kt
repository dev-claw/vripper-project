package me.vripper.vgapi

internal data class SubscriptionsItem(
    val threadItemList: List<ThreadItem>,
    val error: String,
    val errorType: String = "",
    val apiVersion: Int = 0,
    val apiFeatures: Set<String> = emptySet(),
)
