package me.vripper.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoundedCacheTest {
    @Test
    fun `evicts the oldest entry when the limit is exceeded`() {
        val cache = BoundedCache<Int, String>(2)
        cache.put(1, "a")
        cache.put(2, "b")
        cache.put(3, "c")

        assertNull(cache[1])
        assertEquals("b", cache[2])
        assertEquals("c", cache[3])
        assertEquals(2, cache.size)
    }

    @Test
    fun `reading an entry protects it from eviction`() {
        val cache = BoundedCache<Int, String>(2)
        cache.put(1, "a")
        cache.put(2, "b")
        assertEquals("a", cache[1])

        cache.put(3, "c")

        assertEquals("a", cache[1])
        assertNull(cache[2])
        assertEquals("c", cache[3])
    }
}
