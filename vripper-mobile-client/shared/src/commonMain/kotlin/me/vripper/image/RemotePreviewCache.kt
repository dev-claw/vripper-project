package me.vripper.image

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val PREVIEW_CACHE_ENTRIES = 48
private const val PREVIEW_MAX_DIMENSION = 256

/**
 * Small cache for the preview thumbnails shown in post lists and image rows.
 *
 * The previews live on the image host, not on the VRipper server, so they are
 * fetched lazily and kept around to avoid re-downloading while scrolling.
 */
object RemotePreviewCache {
    private val cache = BoundedCache<String, ImageBitmap>(PREVIEW_CACHE_ENTRIES)
    private val mutex = Mutex()

    fun get(url: String): ImageBitmap? = cache[url]

    suspend fun load(url: String): ImageBitmap? {
        val key = url.trim()
        if (key.isEmpty()) return null
        mutex.withLock { cache[key] }?.let { return it }

        val bytes = fetchThumbnailBytes(key) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { decodeImage(bytes, PREVIEW_MAX_DIMENSION) }.getOrNull()
        } ?: return null

        mutex.withLock { cache.put(key, bitmap) }
        return bitmap
    }
}
