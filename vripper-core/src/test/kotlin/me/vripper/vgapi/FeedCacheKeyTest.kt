package me.vripper.vgapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedCacheKeyTest {

    @Test
    fun `is independent of parameter insertion order`() {
        val first = buildFeedCacheKey(
            "https://viper.click/vr.php",
            "user:alice",
            mapOf("threadid" to "42", "since" to "100"),
        )
        val second = buildFeedCacheKey(
            "https://viper.click/vr.php",
            "user:alice",
            mapOf("since" to "100", "threadid" to "42"),
        )

        assertEquals(first, second)
        assertTrue(first.endsWith("|since=100&threadid=42"))
    }

    @Test
    fun `changes when the auth identity changes`() {
        val anonymous = buildFeedCacheKey(
            "https://viper.click/vr.php",
            "anonymous",
            mapOf("threadid" to "42"),
        )
        val authenticated = buildFeedCacheKey(
            "https://viper.click/vr.php",
            "user:alice",
            mapOf("threadid" to "42"),
        )

        assertNotEquals(anonymous, authenticated)
    }

    @Test
    fun `changes when the feed url changes`() {
        val first = buildFeedCacheKey(
            "https://viper.click/vr.php",
            "anonymous",
            mapOf("threadid" to "42"),
        )
        val second = buildFeedCacheKey(
            "https://mirror.example/vr.php",
            "anonymous",
            mapOf("threadid" to "42"),
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `changes when a parameter value changes`() {
        val first = buildFeedCacheKey(
            "https://viper.click/vr.php",
            "anonymous",
            mapOf("since" to "100"),
        )
        val second = buildFeedCacheKey(
            "https://viper.click/vr.php",
            "anonymous",
            mapOf("since" to "200"),
        )

        assertNotEquals(first, second)
    }
}
