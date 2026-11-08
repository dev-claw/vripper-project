package me.vripper.image

/**
 * A tiny least-recently-used cache with a hard entry limit.
 *
 * The image viewer used to keep every decoded bitmap for the whole session, which
 * caused the process to run out of memory after browsing a handful of full size
 * photos. Limiting the cache keeps memory usage predictable while still making
 * back-and-forth swiping cheap.
 */
class BoundedCache<K, V>(private val maxEntries: Int) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val entries = LinkedHashMap<K, V>()
    private val order = ArrayDeque<K>()

    val size: Int
        get() = entries.size

    operator fun get(key: K): V? {
        val value = entries[key] ?: return null
        touch(key)
        return value
    }

    fun put(key: K, value: V) {
        entries[key] = value
        order.remove(key)
        order.addLast(key)
        while (order.size > maxEntries) {
            val evicted = order.removeFirst()
            entries.remove(evicted)
        }
    }

    fun containsKey(key: K): Boolean = entries.containsKey(key)

    fun clear() {
        entries.clear()
        order.clear()
    }

    private fun touch(key: K) {
        if (order.remove(key)) {
            order.addLast(key)
        }
    }
}
