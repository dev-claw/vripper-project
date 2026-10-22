package me.vripper.gui.components.fragments

import javafx.scene.image.Image

/**
 * LRU cache of decoded frames, bounded by total decoded bytes rather than a
 * fixed count (a single HiDPI frame can be tens of times larger than a
 * windowed one). Keeps the decode scale alongside each frame so the deep-zoom
 * logic knows how much detail is already available.
 */
internal class DecodedImageCache(private val maxBytes: Long) {
    private class Frame(val image: Image, val scale: Double, val bytes: Long)

    data class Stats(val frames: Int, val bytes: Long, val maxBytes: Long)

    private val frames = LinkedHashMap<Int, Frame>(16, 0.75f, true)
    private var totalBytes = 0L

    // The index currently on screen; never evicted so the displayed image
    // cannot be dropped by a background preload.
    private var pinned = -1

    @Synchronized
    fun stats(): Stats = Stats(frames.size, totalBytes, maxBytes)

    @Synchronized
    fun pin(index: Int) {
        pinned = index
    }

    @Synchronized
    fun get(index: Int): Image? = frames[index]?.image

    @Synchronized
    fun scaleOf(index: Int): Double = frames[index]?.scale ?: 0.0

    @Synchronized
    fun contains(index: Int): Boolean = frames.containsKey(index)

    @Synchronized
    fun put(index: Int, image: Image, scale: Double) {
        val bytes = image.width.toLong() * image.height.toLong() * 4L
        val previous = frames.put(index, Frame(image, scale, bytes))
        if (previous != null) totalBytes -= previous.bytes
        totalBytes += bytes
        evict()
    }

    @Synchronized
    fun remove(index: Int) {
        frames.remove(index)?.let { totalBytes -= it.bytes }
    }

    @Synchronized
    fun clear() {
        frames.clear()
        totalBytes = 0L
        pinned = -1
    }

    // Evicts least-recently-used frames, never the pinned one, and always
    // keeps at least one frame so a single image larger than the budget can
    // still be shown without being re-decoded on every access.
    private fun evict() {
        val iterator = frames.entries.iterator()
        while (totalBytes > maxBytes && countEvictable() > 1 && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key == pinned) continue
            totalBytes -= entry.value.bytes
            iterator.remove()
        }
    }

    private fun countEvictable(): Int =
        frames.size - if (frames.containsKey(pinned)) 1 else 0
}
