package me.vripper.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import me.vripper.utilities.extractBaseUrl
import me.vripper.vgapi.ThreadItem
import me.vripper.vgapi.ThreadLookupAPIParser
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

internal class ThreadCacheService(val dataAccessService: DataAccessService) {

    private val cache: LoadingCache<Long, ThreadItem> =
        Caffeine.newBuilder().expireAfterWrite(20, TimeUnit.MINUTES).build(::cacheLoader)

    fun invalidate() {
        cache.invalidateAll()
    }

    operator fun get(threadId: Long): ThreadItem {
        return cache[threadId]
    }

    fun getIfPresent(threadId: Long): ThreadItem? {
        return cache.getIfPresent(threadId)
    }

    fun loadThenCache(threadId: Long, siteProxy: String): ThreadItem {
        return cache.get(threadId) {
            cacheLoader(threadId, siteProxy)
        }
    }

    private fun cacheLoader(threadId: Long, siteProxy: String? = null): ThreadItem? {
        val result = if (siteProxy != null) {
            ThreadLookupAPIParser(siteProxy, threadId).parse()
        } else {
            dataAccessService.findThreadByThreadId(threadId).map { threadEntity ->
                ThreadLookupAPIParser(threadEntity.link.extractBaseUrl(), threadId).parse()
            }.getOrNull()
        }

        if (result == null) {
            throw Exception("Failed to load thread $threadId")
        }

        dataAccessService.findThreadByThreadId(result.threadId).ifPresent {
            if (result.postItemList.isNotEmpty()) {
                dataAccessService.update(it.copy(total = result.postItemList.size))
            }
        }
        return result
    }
}