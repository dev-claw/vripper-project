package me.vripper.image

/**
 * Downloads a thumbnail from its public URL.
 *
 * Thumbnails are hosted by the image provider, not by the VRipper server, so this
 * is a best effort call. Returning `null` simply means the full image will be
 * shown without a low resolution placeholder.
 */
expect suspend fun fetchThumbnailBytes(url: String): ByteArray?
