package me.vripper.services

import me.vripper.entities.ThreadEntity
import me.vripper.vgapi.ThreadItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThreadCacheMetadataTest {

    @Test
    fun `applies api total and newest post id directly`() {
        val existing = ThreadEntity(
            id = 1,
            title = "Existing title",
            link = "https://proxy.example/threads/42",
            threadId = 42,
            total = 10,
            newestPostId = 100,
        )
        val incoming = ThreadItem(
            threadId = 42,
            title = "Updated title",
            securityToken = "",
            forum = "Cars",
            postItemList = emptyList(),
            error = "",
            replyCount = 472,
            summaryPosts = 473,
            newestPostId = 268204921,
        )

        val merged = applyThreadMetadata(existing, incoming)

        assertEquals("Updated title", merged.title)
        assertEquals("Cars", merged.forum)
        assertEquals(473, merged.total)
        assertEquals(268204921, merged.newestPostId)
    }
}
