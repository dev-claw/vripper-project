package me.vripper.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import me.vripper.vgapi.ThreadItem
import me.vripper.vgapi.ThreadLookupAPIParser
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class ThreadCacheService(val dataAccessService: DataAccessService) {

    private val cache: LoadingCache<Long, ThreadItem> =
        Caffeine.newBuilder().expireAfterWrite(20, TimeUnit.MINUTES).build { threadId ->
            val threadItem = ThreadLookupAPIParser(threadId).parse()
            dataAccessService.findThreadByThreadId(threadItem.threadId).ifPresent {
                if (threadItem.postItemList.isNotEmpty()) {
                    dataAccessService.update(it.copy(total = threadItem.postItemList.size))
                }
            }
            threadItem
        }

    fun invalidate() {
        cache.invalidateAll()
    }

    @Throws(ExecutionException::class)
    operator fun get(threadId: Long): ThreadItem {
        return cache[threadId]
    }

    fun getIfPresent(threadId: Long): ThreadItem? {
        return cache.getIfPresent(threadId)
    }
}