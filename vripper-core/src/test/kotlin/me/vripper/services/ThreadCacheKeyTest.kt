package me.vripper.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ThreadCacheKeyTest {

    @Test
    fun `normalizes whitespace and trailing slashes in the site proxy`() {
        val expected = ThreadCacheService.ThreadCacheKey.normalize("https://proxy.example", 42)

        assertEquals(
            expected,
            ThreadCacheService.ThreadCacheKey.normalize(" https://proxy.example/ ", 42),
        )
        assertEquals(
            expected,
            ThreadCacheService.ThreadCacheKey.normalize("https://proxy.example///", 42),
        )
    }

    @Test
    fun `keeps different threads apart`() {
        val first = ThreadCacheService.ThreadCacheKey.normalize("https://proxy.example", 42)
        val second = ThreadCacheService.ThreadCacheKey.normalize("https://proxy.example", 43)

        assertNotEquals(first, second)
    }
}
