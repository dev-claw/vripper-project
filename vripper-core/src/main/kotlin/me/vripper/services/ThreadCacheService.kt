package me.vripper.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import me.vripper.entities.ThreadEntity
import me.vripper.vgapi.ThreadItem
import me.vripper.vgapi.ThreadLookupAPIParser
import java.util.concurrent.TimeUnit

internal class ThreadCacheService(val dataAccessService: DataAccessService) {

    private val cache: LoadingCache<ThreadCacheKey, ThreadItem> =
        Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .maximumSize(MAX_CACHED_THREADS)
            .build(::cacheLoader)

    fun invalidate() {
        cache.invalidateAll()
    }

    fun getIfPresent(siteProxy: String, threadId: Long): ThreadItem? {
        return cache.getIfPresent(ThreadCacheKey.normalize(siteProxy, threadId))
    }

    fun loadThenCache(threadId: Long, siteProxy: String): ThreadItem {
        val key = ThreadCacheKey.normalize(siteProxy, threadId)
        return cache.get(key) { cacheLoader(it) }
    }

    fun loadSince(threadId: Long, siteProxy: String, since: Long): ThreadItem {
        val result = ThreadLookupAPIParser(siteProxy, threadId, since).parse()
        if (result.error.isBlank()) {
            dataAccessService.findThreadByThreadId(result.threadId).ifPresent {
                dataAccessService.update(applyMetadata(it, result))
            }
            cache.invalidate(ThreadCacheKey.normalize(siteProxy, threadId))
        }
        return result
    }

    fun loadMetadata(threadId: Long, siteProxy: String, since: Long): ThreadItem {
        val result = ThreadLookupAPIParser(siteProxy, threadId, since).parseMetadata()
        if (result.error.isBlank()) {
            dataAccessService.findThreadByThreadId(result.threadId).ifPresent {
                dataAccessService.update(applyMetadata(it, result))
            }
            cache.invalidate(ThreadCacheKey.normalize(siteProxy, threadId))
        }
        return result
    }

    private fun cacheLoader(key: ThreadCacheKey): ThreadItem {
        val result = ThreadLookupAPIParser(key.siteProxy, key.threadId).parse()

        dataAccessService.findThreadByThreadId(result.threadId).ifPresent {
            if (result.postItemList.isNotEmpty()) {
                dataAccessService.update(applyMetadata(it, result))
            }
        }
        return result
    }

    internal data class ThreadCacheKey(val siteProxy: String, val threadId: Long) {
        companion object {
            fun normalize(siteProxy: String, threadId: Long): ThreadCacheKey =
                ThreadCacheKey(siteProxy.trim().trimEnd('/'), threadId)
        }
    }

    internal fun applyMetadata(
        thread: ThreadEntity,
        result: ThreadItem,
    ): ThreadEntity = applyThreadMetadata(thread, result)

    companion object {
        private const val MAX_CACHED_THREADS = 64L
    }
}

internal fun applyThreadMetadata(
    thread: ThreadEntity,
    result: ThreadItem,
): ThreadEntity {
    return thread.copy(
        title = result.title,
        total = result.replyCount + 1,
        forumId = result.forumId,
        forum = result.forum,
        prefixId = result.prefixId,
        starter = result.starter,
        starterId = result.starterId,
        created = result.created,
        lastPost = result.lastPost,
        lastPostId = result.lastPostId,
        lastPoster = result.lastPoster,
        replyCount = result.replyCount,
        views = result.views,
        open = result.open,
        sticky = result.sticky,
        newestPostId = result.newestPostId,
    )
}
