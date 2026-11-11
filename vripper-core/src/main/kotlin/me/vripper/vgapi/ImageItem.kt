package me.vripper.vgapi

import me.vripper.host.Host

internal data class ImageItem(
    val mainLink: String,
    val thumbLink: String,
    val host: Host,
    val hostName: String = "",
    val duplicate: Boolean = false,
)
