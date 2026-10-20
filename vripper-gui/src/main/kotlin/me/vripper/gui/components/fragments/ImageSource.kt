package me.vripper.gui.components.fragments

import kotlinx.coroutines.flow.Flow
import me.vripper.model.ImageChunk

interface ImageSource {
    /**
     * Reads the encoded bytes of the full-size image, or null when the image is
     * not available (not downloaded yet, or the file was removed).
     */
    suspend fun readBytes(): ByteArray?
    fun fileName(): String

    /**
     * Updates the file name once it becomes known, e.g. after a download
     * completes. Ignored by default.
     */
    fun updateFileName(name: String) {}

    /**
     * Stable identity used as the key when caching the encoded bytes.
     */
    fun cacheKey(): String

    /**
     * Remote URL of the thumbnail for this image, or an empty string when the
     * source has no thumbnail. Used by the viewer's browse mode. Mutable so the
     * viewer can pick up a thumbnail that becomes available later.
     */
    var thumbUrl: String

    /**
     * Identifier of the owning image/post, used as part of the thumbnail cache
     * key.
     */
    val postEntityId: Long get() = -1L

    /**
     * Unique identifier of the image, used to reconcile refreshed state.
     */
    val imageId: Long get() = -1L

    /**
     * Whether the full-size image has already been downloaded. Mutable so the
     * viewer can refresh it when a background download completes.
     */
    var downloaded: Boolean
}

data class BytesImageSource(
    val id: Long,
    var fileName: String,
    override var thumbUrl: String = "",
    override val postEntityId: Long = -1L,
    override var downloaded: Boolean = false,
    val downloadFunction: () -> Flow<ImageChunk>
) : ImageSource {

    override val imageId: Long
        get() = id

    override suspend fun readBytes(): ByteArray? {
        // Collect the chunks and join them once instead of buffering through a
        // ByteArrayOutputStream and then copying the result again.
        val chunks = ArrayList<ByteArray>()
        var missing = false
        downloadFunction().collect {
            if (it.missing) {
                missing = true
            } else if (it.data.isNotEmpty()) {
                chunks.add(it.data)
            }
        }
        if (missing) return null
        return when (chunks.size) {
            0 -> ByteArray(0)
            1 -> chunks[0]
            else -> {
                val bytes = ByteArray(chunks.sumOf { it.size })
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(bytes, offset)
                    offset += chunk.size
                }
                bytes
            }
        }
    }

    override fun fileName(): String {
        return fileName
    }

    override fun updateFileName(name: String) {
        if (name.isNotBlank()) {
            fileName = name
        }
    }

    override fun cacheKey(): String {
        return "image:$id"
    }
}