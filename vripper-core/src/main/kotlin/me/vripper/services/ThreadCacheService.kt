package me.vripper.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import me.vripper.event.EventBus
import me.vripper.event.SettingsUpdateEvent
import me.vripper.vgapi.ThreadItem
import me.vripper.vgapi.ThreadLookupAPIParser
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class ThreadCacheService(val eventBus: EventBus, val dataTransaction: DataTransaction) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init() {
        coroutineScope.launch {
            eventBus.events.filterIsInstance(SettingsUpdateEvent::class).collect {
                cache.invalidateAll()
            }
        }
    }

    private val cache: LoadingCache<Long, ThreadItem> =
        Caffeine.newBuilder().expireAfterWrite(20, TimeUnit.MINUTES).build { threadId ->
            val threadItem = ThreadLookupAPIParser(threadId).parse()
            dataTransaction.findThreadByThreadId(threadItem.threadId).ifPresent {
                if (threadItem.postItemList.isNotEmpty()) {
                    dataTransaction.update(it.copy(total = threadItem.postItemList.size))
                }
            }
            threadItem
        }

    @Throws(ExecutionException::class)
    operator fun get(threadId: Long): ThreadItem {
        return cache[threadId]
    }

    fun getIfPresent(threadId: Long): ThreadItem? {
        return cache.getIfPresent(threadId)
    }
}