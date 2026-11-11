package me.vripper.tasks

import me.vripper.event.EventBus
import me.vripper.event.ThreadNewPostsEvent
import me.vripper.services.DataAccessService
import me.vripper.services.ThreadCacheService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.extractBaseUrl
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class ThreadRefreshTask : KoinComponent, ITask {
    private val log by LoggerDelegate()
    private val dataAccessService: DataAccessService by inject()
    private val threadCacheService: ThreadCacheService by inject()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Refreshing thread library"

    override fun getCategory() = TaskCategory.THREAD

    override fun getDescription() = "Refreshing thread metadata without queueing posts"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        val updated = mutableListOf<Long>()
        val failed = mutableListOf<Pair<Long, String>>()
        try {
            val threads = dataAccessService.findAllThreadsAsync()
            threads.forEach { thread ->
                try {
                    val siteProxy = thread.link.extractBaseUrl()
                    val refresh = threadCacheService.loadMetadata(
                        thread.threadId,
                        siteProxy,
                        thread.newestPostId,
                    )
                    if (refresh.error.isNotBlank()) {
                        failed.add(thread.threadId to refresh.error)
                    } else {
                        updated.add(thread.threadId)
                        if (thread.newestPostId > 0 && refresh.newestPostId > thread.newestPostId) {
                            EventBus.publishEvent(
                                ThreadNewPostsEvent(
                                    threadId = thread.threadId,
                                    title = refresh.title.ifBlank { thread.title },
                                    link = thread.link,
                                    newPosts = (refresh.replyCount + 1 - thread.total).coerceAtLeast(1),
                                    subscription = false,
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error when refreshing thread ${thread.threadId}", e)
                    failed.add(thread.threadId to (e.message ?: e.toString()))
                }
            }
            status = if (failed.isNotEmpty()) TaskStatus.WARNING else TaskStatus.SUCCESS
            result = buildString {
                append("Refreshed ")
                append(updated.size)
                append(" thread(s), failed ")
                append(failed.size)
                if (failed.isNotEmpty()) {
                    append('\n')
                    append("Failed:")
                    failed.forEach { (threadId, reason) ->
                        append("\n  - thread #").append(threadId).append(": ").append(reason)
                    }
                }
            }
        } catch (e: Exception) {
            result = e.message ?: "Error when refreshing threads"
            status = TaskStatus.FAILURE
            log.error("Error when refreshing threads", e)
        }
    }
}
