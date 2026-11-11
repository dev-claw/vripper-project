package me.vripper.tasks

import me.vripper.entities.SubscriptionEntity
import me.vripper.event.EventBus
import me.vripper.event.ThreadNewPostsEvent
import me.vripper.services.DataAccessService
import me.vripper.services.SettingsService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.ViperLinks
import me.vripper.vgapi.SubscriptionsAPIParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class SubscriptionsTask : KoinComponent, ITask {
    private val log by LoggerDelegate()
    private val dataAccessService: DataAccessService by inject()
    private val settingsService: SettingsService by inject()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Refreshing subscriptions"

    override fun getCategory() = TaskCategory.THREAD

    override fun getDescription() = "Refreshing subscribed threads"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        val imported = mutableListOf<Long>()
        val updated = mutableListOf<Long>()
        val removed = mutableListOf<Long>()
        val failed = mutableListOf<Pair<Long, String>>()
        try {
            val siteProxy = settingsService.settings.viperSettings.host
            val subscriptions = SubscriptionsAPIParser(siteProxy).parse()
            if (subscriptions.error.isNotBlank()) {
                status = TaskStatus.WARNING
                result = "Unable to load subscriptions: ${subscriptions.error}"
                return
            }

            val incoming = subscriptions.threadItemList.distinctBy { it.threadId }
            val existingByThreadId = dataAccessService.findAllSubscriptionsAsync()
                .associateBy { it.threadId }
            incoming.forEach { threadItem ->
                try {
                    val existing = existingByThreadId[threadItem.threadId]
                    val subscription = SubscriptionEntity(
                        id = existing?.id ?: -1,
                        title = threadItem.title,
                        link = ViperLinks.threadUrl(siteProxy, threadItem.threadId),
                        threadId = threadItem.threadId,
                        total = threadItem.replyCount + 1,
                        forumId = threadItem.forumId,
                        forum = threadItem.forum,
                        prefixId = threadItem.prefixId,
                        starter = threadItem.starter,
                        starterId = threadItem.starterId,
                        created = threadItem.created,
                        lastPost = threadItem.lastPost,
                        lastPostId = threadItem.lastPostId,
                        lastPoster = threadItem.lastPoster,
                        replyCount = threadItem.replyCount,
                        views = threadItem.views,
                        open = threadItem.open,
                        sticky = threadItem.sticky,
                        newestPostId = threadItem.newestPostId,
                    )
                    when {
                        existing == null -> {
                            dataAccessService.saveSubscriptionAsync(subscription)
                            imported.add(threadItem.threadId)
                        }

                        existing != subscription -> {
                            dataAccessService.updateSubscriptionAsync(subscription)
                            updated.add(threadItem.threadId)
                        }
                    }
                    if (existing != null &&
                        existing.newestPostId > 0 &&
                        threadItem.newestPostId > existing.newestPostId
                    ) {
                        EventBus.publishEvent(
                            ThreadNewPostsEvent(
                                threadId = threadItem.threadId,
                                title = threadItem.title.ifBlank { existing.title },
                                link = subscription.link,
                                newPosts = (threadItem.replyCount + 1 - existing.total).coerceAtLeast(1),
                                subscription = true,
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.error("Error when refreshing subscribed thread ${threadItem.threadId}", e)
                    failed.add(threadItem.threadId to (e.message ?: e.toString()))
                }
            }

            val fetchedThreadIds = incoming.map { it.threadId }.toSet()
            existingByThreadId.keys.filterNot { it in fetchedThreadIds }.forEach { threadId ->
                try {
                    dataAccessService.removeSubscriptionAsync(threadId)
                    removed.add(threadId)
                } catch (e: Exception) {
                    log.error("Error when removing subscribed thread $threadId", e)
                    failed.add(threadId to (e.message ?: e.toString()))
                }
            }

            status = if (failed.isNotEmpty()) TaskStatus.WARNING else TaskStatus.SUCCESS
            result = summarize(imported, updated, removed, failed)
        } catch (e: Exception) {
            result = e.message ?: "Error when refreshing subscriptions"
            status = TaskStatus.FAILURE
            log.error("Error when refreshing subscriptions", e)
        }
    }

    private fun summarize(
        imported: List<Long>,
        updated: List<Long>,
        removed: List<Long>,
        failed: List<Pair<Long, String>>,
    ): String = buildString {
        append("Imported ")
        append(imported.size)
        append(", refreshed ")
        append(updated.size)
        append(", removed ")
        append(removed.size)
        append(", failed ")
        append(failed.size)
        appendThreadIds("New subscriptions:", imported)
        appendThreadIds("Removed subscriptions:", removed)
        if (failed.isNotEmpty()) {
            append('\n')
            append("Failed:")
            failed.forEach { (threadId, reason) ->
                append("\n  - thread #").append(threadId).append(": ").append(reason)
            }
        }
    }

    private fun StringBuilder.appendThreadIds(label: String, threadIds: List<Long>) {
        if (threadIds.isEmpty()) {
            return
        }
        append('\n')
        append(label)
        threadIds.take(MAX_DETAILED_THREADS).forEach { append("\n  - #").append(it) }
        if (threadIds.size > MAX_DETAILED_THREADS) {
            append("\n  ... and ")
            append(threadIds.size - MAX_DETAILED_THREADS)
            append(" more")
        }
    }

    companion object {
        private const val MAX_DETAILED_THREADS = 10
    }
}
