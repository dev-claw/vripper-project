package me.vripper.tasks

import me.vripper.entities.ThreadEntity
import me.vripper.model.PostIdentifier
import me.vripper.model.Settings
import me.vripper.model.ThreadIdentifier
import me.vripper.services.DataAccessService
import me.vripper.services.TaskService
import me.vripper.services.ThreadCacheService
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class ThreadLookupTask(
    private val threadIdentifiers: List<ThreadIdentifier>,
    private val settings: Settings
) : KoinComponent, ITask {
    private val log by LoggerDelegate()
    private val dataAccessService by inject<DataAccessService>()
    private val taskService by inject<TaskService>()
    private val threadCacheService by inject<ThreadCacheService>()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Looking up threads"

    override fun getCategory() = TaskCategory.THREAD

    override fun getDescription() = "Looking up ${threadIdentifiers.size} thread(s)"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        val total = threadIdentifiers.size
        val queued = mutableListOf<Pair<Long, Int>>()
        val saved = mutableListOf<Pair<Long, Int>>()
        val alreadyQueued = mutableListOf<Long>()
        val failed = mutableListOf<Pair<Long, String>>()
        try {
            threadIdentifiers.forEach { threadIdentifier ->
                val link = "${threadIdentifier.siteProxy}/threads/${threadIdentifier.threadId}"
                try {
                    if (!dataAccessService.findThreadByThreadId(threadIdentifier.threadId).isEmpty) {
                        log.info("Thread ${threadIdentifier.threadId} is already queued")
                        alreadyQueued.add(threadIdentifier.threadId)
                        return@forEach
                    }
                    val threadLookupResult =
                        threadCacheService.loadThenCache(threadIdentifier.threadId, threadIdentifier.siteProxy)
                    when {
                        threadLookupResult.error.isNotBlank() -> {
                            log.error("Error loading $link: ${threadLookupResult.error}")
                            failed.add(threadIdentifier.threadId to "Error loading $link: ${threadLookupResult.error}")
                        }

                        threadLookupResult.postItemList.isEmpty() -> {
                            log.error("Nothing found for $link")
                            failed.add(threadIdentifier.threadId to "Nothing found for $link")
                        }

                        threadLookupResult.postItemList.size <= settings.downloadSettings.autoQueueThreshold -> {
                            taskService.submit(
                                QueueItemsTask(threadLookupResult.postItemList.map {
                                    PostIdentifier(
                                        threadIdentifier.siteProxy, it.threadId, it.postId
                                    )
                                })
                            )
                            queued.add(threadIdentifier.threadId to threadLookupResult.postItemList.size)
                        }

                        else -> {
                            dataAccessService.save(
                                ThreadEntity(
                                    title = threadLookupResult.title,
                                    link = link,
                                    threadId = threadIdentifier.threadId,
                                    total = threadLookupResult.postItemList.size
                                )
                            )
                            saved.add(threadIdentifier.threadId to threadLookupResult.postItemList.size)
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error when processing $link", e)
                    failed.add(threadIdentifier.threadId to "Error when processing $link: ${e.message ?: e.toString()}")
                }
            }

            status = if (failed.isNotEmpty()) TaskStatus.WARNING else TaskStatus.SUCCESS
            result = summarize(total, queued, saved, alreadyQueued, failed)
        } catch (e: Exception) {
            result = e.message ?: "Error when looking up threads"
            status = TaskStatus.FAILURE
            log.error("Error when looking up threads", e)
        }
    }

    private fun summarize(
        total: Int,
        queued: List<Pair<Long, Int>>,
        saved: List<Pair<Long, Int>>,
        alreadyQueued: List<Long>,
        failed: List<Pair<Long, String>>,
    ): String = buildString {
        append("Processed ")
        append(total)
        append(" thread(s) - Queued: ")
        append(queued.size)
        append(", Saved: ")
        append(saved.size)
        append(", Already queued: ")
        append(alreadyQueued.size)
        append(", Failed: ")
        append(failed.size)
        if (queued.isNotEmpty()) {
            append('\n')
            append("Queued:")
            queued.forEach { (threadId, count) ->
                append('\n')
                append("  - thread #")
                append(threadId)
                append(" (")
                append(count)
                append(" post(s))")
            }
        }
        if (saved.isNotEmpty()) {
            append('\n')
            append("Saved for later:")
            saved.forEach { (threadId, count) ->
                append('\n')
                append("  - thread #")
                append(threadId)
                append(" (")
                append(count)
                append(" post(s))")
            }
        }
        if (alreadyQueued.isNotEmpty()) {
            append('\n')
            append("Already queued: ")
            append(alreadyQueued.joinToString(", ") { "#$it" })
        }
        if (failed.isNotEmpty()) {
            append('\n')
            append("Failed:")
            failed.forEach { (threadId, reason) ->
                append('\n')
                append("  - thread #")
                append(threadId)
                append(": ")
                append(reason)
            }
        }
    }
}
