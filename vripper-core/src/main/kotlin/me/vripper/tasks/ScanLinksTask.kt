package me.vripper.tasks

import me.vripper.model.PostIdentifier
import me.vripper.model.ThreadIdentifier
import me.vripper.services.IPostCommandEndpoint
import me.vripper.services.SettingsService
import me.vripper.services.TaskService
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.regex.Pattern

internal class ScanLinksTask(
    private val postLinks: String,
    private val origin: IPostCommandEndpoint.ScanOrigin,
) : KoinComponent, ITask {

    private val log by LoggerDelegate()
    private val settingsService: SettingsService by inject()
    private val taskService: TaskService by inject()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Scanning links"

    override fun getCategory() = TaskCategory.SCAN

    override fun getDescription() = "${origin.str} links"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        try {
            if (postLinks.isBlank()) {
                status = TaskStatus.WARNING
                result = "No links to scan"
                return
            }
            val postsFound = mutableListOf<PostIdentifier>()
            val threadFound = mutableListOf<ThreadIdentifier>()
            val invalid = mutableListOf<String>()
            val urlList = postLinks.split(Pattern.compile("\\r?\\n")).dropLastWhile { it.isBlank() }.map { it.trim() }
                .filter { it.isNotEmpty() }
            val proxies = settingsService.getProxies()
            for (link in urlList) {
                log.debug("Scanning: $link")
                val matchingProxy = proxies.find { link.startsWith(it) }
                if (matchingProxy == null) {
                    invalid.add(link)
                    continue
                }
                var threadId: Long
                var postId: Long?
                val m = Pattern.compile(
                    Pattern.quote(matchingProxy) + "/threads/(\\d+)((.*p=)(\\d+))?"
                ).matcher(link)
                if (m.find()) {
                    threadId = m.group(1).toLong()
                    postId = m.group(4)?.toLong()
                    if (postId == null) {
                        threadFound.add(ThreadIdentifier(matchingProxy, threadId))
                    } else {
                        postsFound.add(PostIdentifier(matchingProxy, threadId, postId))
                    }
                } else {
                    log.error("Invalid link $link, link is missing the threadId")
                    invalid.add(link)
                    continue
                }
            }
            if (threadFound.isNotEmpty()) {
                taskService.submit(
                    ThreadLookupTask(
                        threadFound, settingsService.settings
                    )
                )
            }
            if (postsFound.isNotEmpty()) {
                taskService.submit(
                    QueueItemsTask(
                        postsFound
                    )
                )
            }
            status = if (invalid.isNotEmpty()) TaskStatus.WARNING else TaskStatus.SUCCESS
            result = summarize(threadFound.size, postsFound.size, invalid)
        } catch (e: Exception) {
            status = TaskStatus.FAILURE
            result = e.message ?: "Error when scanning links"
            log.error("Error when scanning links", e)
        }
    }

    private fun summarize(
        threadCount: Int,
        postCount: Int,
        invalid: List<String>,
    ): String = buildString {
        append("Found ")
        append(threadCount)
        append(" thread(s) and ")
        append(postCount)
        append(" post(s)")
        if (invalid.isNotEmpty()) {
            append('\n')
            append("Ignored ${invalid.size} invalid link(s):")
            invalid.forEach {
                append('\n')
                append("  - ")
                append(it)
            }
        }
    }
}
