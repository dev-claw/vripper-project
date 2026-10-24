package me.vripper.tasks

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.vripper.entities.PostEntity
import me.vripper.model.PostIdentifier
import me.vripper.services.DataAccessService
import me.vripper.services.MetadataService
import me.vripper.services.SettingsService
import me.vripper.services.ThreadCacheService
import me.vripper.services.download.DownloadService
import me.vripper.utilities.LoggerDelegate
import me.vripper.vgapi.PostItem
import me.vripper.vgapi.PostLookupAPIParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class QueueItemsTask(private val items: List<PostIdentifier>) : KoinComponent, ITask {
    private val log by LoggerDelegate()
    private val dataAccessService: DataAccessService by inject()
    private val settingsService: SettingsService by inject()
    private val downloadService: DownloadService by inject()
    private val threadCacheService: ThreadCacheService by inject()
    private val metadataService: MetadataService by inject()
    private var status = TaskStatus.WAITING
    private var result = ""

    companion object {
        val lock = Mutex()
    }

    override fun getName() = "Queuing galleries"

    override fun getCategory() = TaskCategory.QUEUE

    override fun getDescription() = "Adding galleries to the download queue"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        lock.withLock {
            status = TaskStatus.RUNNING
            val total = items.size
            val toProcess = mutableListOf<PostItem>()
            val alreadyLoaded = mutableListOf<Long>()
            val failed = mutableListOf<Pair<Long, String>>()
            try {
                for ((siteProxy, threadId, postId) in items) {
                    val link =
                        "$siteProxy/threads/$threadId?p=$postId&viewfull=1#post$postId"

                    if (dataAccessService.existsPostId(postId)) {
                        log.info("Post $postId already loaded")
                        alreadyLoaded.add(postId)
                        continue
                    }

                    val threadItem = try {
                        threadCacheService.getIfPresent(threadId)
                            ?: PostLookupAPIParser(siteProxy, threadId, postId).parse()
                    } catch (e: Exception) {
                        log.error("Failed to load $link", e)
                        failed.add(postId to "Failed to load $link: ${e.message ?: e.toString()}")
                        continue
                    }

                    when {
                        threadItem == null -> {
                            log.error("Failed to load $link")
                            failed.add(postId to "Failed to load $link")
                        }

                        threadItem.error.isNotBlank() -> {
                            log.error("Error loading $link: ${threadItem.error}")
                            failed.add(postId to "Error loading $link: ${threadItem.error}")
                        }

                        threadItem.postItemList.isEmpty() -> {
                            log.error("Nothing found for $link")
                            failed.add(postId to "Nothing found for $link")
                        }

                        else -> {
                            val postItem = threadItem.postItemList.firstOrNull { it.postId == postId }
                            if (postItem == null) {
                                log.error("Unable to load $link")
                                failed.add(postId to "Unable to load $link")
                            } else {
                                toProcess.add(postItem)
                            }
                        }
                    }
                }

                if (toProcess.isEmpty()) {
                    status = TaskStatus.WARNING
                    result = summarize(total, emptyList(), alreadyLoaded, failed)
                    return
                }

                val posts = try {
                    dataAccessService.newPosts(toProcess.toList())
                } catch (e: Exception) {
                    log.error("Error when saving galleries", e)
                    result = buildString {
                        append("Failed to save ${toProcess.size} of $total post(s): ")
                        append(e.message ?: e.toString())
                        appendDetails(emptyList(), alreadyLoaded, failed)
                    }
                    status = TaskStatus.FAILURE
                    return
                }

                posts.forEach {
                    metadataService.fetchMetadata(it)
                }
                if (settingsService.settings.downloadSettings.autoStart) {
                    downloadService.restartAll(posts)
                }

                val nonNormal = alreadyLoaded.isNotEmpty() || failed.isNotEmpty() || posts.size != toProcess.size
                status = if (nonNormal) TaskStatus.WARNING else TaskStatus.SUCCESS
                result = summarize(total, posts, alreadyLoaded, failed)
            } catch (e: Exception) {
                result = e.message ?: "Error when adding galleries"
                status = TaskStatus.FAILURE
                log.error("Error when adding galleries", e)
            }
        }
    }

    private fun summarize(
        total: Int,
        added: List<PostEntity>,
        alreadyLoaded: List<Long>,
        failed: List<Pair<Long, String>>,
    ): String = buildString {
        append("Processed ")
        append(added.size)
        append(" of ")
        append(total)
        append(" URL(s) - Added: ")
        append(added.size)
        append(", Already loaded: ")
        append(alreadyLoaded.size)
        append(", Failed: ")
        append(failed.size)
        appendDetails(added, alreadyLoaded, failed)
        if (added.isEmpty() && alreadyLoaded.isEmpty() && failed.isEmpty()) {
            append('\n')
            append("No posts matched the requested URLs")
        }
    }

    private fun StringBuilder.appendDetails(
        added: List<PostEntity>,
        alreadyLoaded: List<Long>,
        failed: List<Pair<Long, String>>,
    ) {
        if (added.isNotEmpty()) {
            append('\n')
            append("Added:")
            added.forEach {
                append('\n')
                append("  - #")
                append(it.vgPostId)
                append(" \"")
                append(it.postTitle)
                append("\" (")
                append(it.total)
                append(" image(s))")
            }
        }
        if (alreadyLoaded.isNotEmpty()) {
            append('\n')
            append("Already loaded: ")
            append(alreadyLoaded.joinToString(", ") { "#$it" })
        }
        if (failed.isNotEmpty()) {
            append('\n')
            append("Failed:")
            failed.forEach { (postId, reason) ->
                append('\n')
                append("  - #")
                append(postId)
                append(": ")
                append(reason)
            }
        }
    }
}
