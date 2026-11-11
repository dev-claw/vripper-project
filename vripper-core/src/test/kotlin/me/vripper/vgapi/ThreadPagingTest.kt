package me.vripper.vgapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThreadPagingTest {

    @Test
    fun `follows next_after until more is false`() {
        val requestedSince = mutableListOf<Long>()
        val pages = mapOf(
            0L to page(0, 100, true, 1, 2),
            100L to page(100, 200, true, 3, 4),
            200L to page(200, 0, false, 5),
        )

        val result = collectThreadPages(THREAD_ID, 0) { since, _ ->
            requestedSince.add(since)
            pages.getValue(since)
        }

        assertEquals(listOf(0L, 100L, 200L), requestedSince)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), result.postItemList.map { it.postId })
        assertEquals(5, result.summaryImages)
        assertEquals(5, result.pagerPagePosts)
        assertEquals(0, result.pagerAfter)
        assertEquals(0, result.pagerNextAfter)
        assertFalse(result.pagerMore)
        assertTrue(result.error.isBlank())
    }

    @Test
    fun `starts paging from the requested since`() {
        val requestedSince = mutableListOf<Long>()
        val pages = mapOf(
            50L to page(50, 100, true, 6),
            100L to page(100, 0, false, 7),
        )

        val result = collectThreadPages(THREAD_ID, 50) { since, _ ->
            requestedSince.add(since)
            pages.getValue(since)
        }

        assertEquals(listOf(50L, 100L), requestedSince)
        assertEquals(listOf(6L, 7L), result.postItemList.map { it.postId })
    }

    @Test
    fun `fails instead of looping when next_after does not advance`() {
        val result = collectThreadPages(THREAD_ID, 0) { _, _ ->
            page(0, 0, true, 1)
        }

        assertTrue(result.error.contains("did not advance"))
        assertEquals("paging", result.errorType)
        assertFalse(result.pagerMore)
    }

    @Test
    fun `passes seen links to subsequent pages`() {
        val seenSnapshots = mutableListOf<Set<String>>()
        val pages = mapOf(
            0L to page(0, 100, true, 1),
            100L to page(100, 0, false, 2),
        )

        val result = collectThreadPages(THREAD_ID, 0) { since, seen ->
            seenSnapshots.add(seen.toSet())
            seen.add("https://example.test/$since")
            pages.getValue(since)
        }

        assertEquals(
            listOf(emptySet(), setOf("https://example.test/0")),
            seenSnapshots,
        )
        assertEquals(listOf(1L, 2L), result.postItemList.map { it.postId })
    }

    @Test
    fun `keeps posts collected before a mid-paging error`() {
        val pages = mapOf(
            0L to page(0, 100, true, 1, 2),
            100L to page(100, 0, false).copy(error = "boom", errorType = "protocol"),
        )

        val result = collectThreadPages(THREAD_ID, 0) { since, _ ->
            pages.getValue(since)
        }

        assertEquals(listOf(1L, 2L), result.postItemList.map { it.postId })
        assertEquals("boom", result.error)
        assertEquals("protocol", result.errorType)
        assertFalse(result.pagerMore)
    }

    @Test
    fun `stops after the maximum number of pages`() {
        var fetchedPages = 0
        val result = collectThreadPages(THREAD_ID, 0) { since, _ ->
            fetchedPages++
            page(since, since + 1, true, since)
        }

        assertEquals(1000, fetchedPages)
        assertTrue(result.error.contains("exceeded 1000 pages"))
        assertEquals("paging", result.errorType)
        assertFalse(result.pagerMore)
    }

    private fun page(
        after: Long,
        nextAfter: Long,
        more: Boolean,
        vararg postIds: Long,
    ): ThreadItem = ThreadItem(
        threadId = THREAD_ID,
        title = "Thread",
        securityToken = "token",
        forum = "Forum",
        postItemList = postIds.map { post(it) },
        error = "",
        pagerAfter = after,
        pagerNextAfter = nextAfter,
        pagerMore = more,
        pagerLimit = 500,
        pagerPagePosts = postIds.size,
    )

    private fun post(postId: Long): PostItem = PostItem(
        threadId = THREAD_ID,
        threadTitle = "Thread",
        postId = postId,
        number = postId.toInt(),
        title = "Post $postId",
        imageCount = 1,
        url = "https://example.test/$postId",
        hosts = emptyList(),
        securityToken = "token",
        forum = "Forum",
        imageItemList = emptyList(),
    )

    private companion object {
        private const val THREAD_ID = 42L
    }
}
