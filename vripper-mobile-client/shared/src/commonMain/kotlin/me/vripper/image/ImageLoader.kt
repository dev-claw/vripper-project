package me.vripper.image

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.vripper.api.EndpointApi
import me.vripper.data.Image as ImageModel

sealed interface ImageLoadResult {
    data class Success(val bitmap: ImageBitmap) : ImageLoadResult
    data object Missing : ImageLoadResult
}

/**
 * Downloads and decodes images while keeping peak memory usage under control.
 *
 * Chunks are copied straight into a single growing buffer instead of being kept
 * in a list and concatenated afterwards. Decoding then happens off the main
 * thread with platform specific downsampling.
 */
class ImageLoader(
    private val api: EndpointApi,
    private val maxImageDimension: Int,
    private val maxPreviewDimension: Int,
) {
    suspend fun loadFull(image: ImageModel): ImageLoadResult {
        val bytes = withContext(Dispatchers.IO) { download(image) } ?: return ImageLoadResult.Missing
        val bitmap = withContext(Dispatchers.Default) {
            try {
                decodeImage(bytes, maxImageDimension)
            } catch (o: OutOfMemoryError) {
                throw IllegalStateException("Not enough memory to display this image", o)
            }
        }
        return ImageLoadResult.Success(bitmap)
    }

    suspend fun loadPreview(image: ImageModel): ImageBitmap? {
        val url = image.thumbUrl.trim()
        if (url.isEmpty()) return null
        val bytes = fetchThumbnailBytes(url) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { decodeImage(bytes, maxPreviewDimension) }.getOrNull()
        }
    }

    private suspend fun download(image: ImageModel): ByteArray? {
        val expectedSize = image.size
            .takeIf { it in 1..MAX_PREALLOCATED_BYTES }
            ?.toInt()
            ?: 0
        val buffer = ByteAccumulator(expectedSize)
        var missing = false

        api.downloadImage(image.id).collect { chunk ->
            when {
                chunk.missing -> missing = true
                chunk.data.isNotEmpty() -> buffer.append(chunk.data)
            }
        }

        return if (missing) null else buffer.toByteArray()
    }

    private companion object {
        const val MAX_PREALLOCATED_BYTES = 32L * 1024L * 1024L
    }
}

private class ByteAccumulator(initialCapacity: Int) {
    private var data = ByteArray(initialCapacity.coerceAtLeast(0))
    private var size = 0

    fun append(chunk: ByteArray) {
        ensureCapacity(size + chunk.size)
        chunk.copyInto(data, size)
        size += chunk.size
    }

    fun toByteArray(): ByteArray =
        if (size == data.size) data else data.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= data.size) return
        var capacity = if (data.isEmpty()) DEFAULT_CAPACITY else data.size
        while (capacity < required) {
            capacity = if (capacity > required / 2 || capacity > Int.MAX_VALUE / 2) {
                required
            } else {
                capacity * 2
            }
        }
        data = data.copyOf(capacity)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 256 * 1024
    }
}
