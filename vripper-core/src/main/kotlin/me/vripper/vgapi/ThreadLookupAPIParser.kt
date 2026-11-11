package me.vripper.vgapi

import dev.failsafe.Failsafe
import dev.failsafe.function.CheckedSupplier
import me.vripper.exception.PostParseException
import me.vripper.services.RetryPolicyService
import me.vripper.services.TaskService
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

internal class ThreadLookupAPIParser(
    private val siteProxy: String,
    private val threadId: Long,
    private val since: Long = 0,
) : KoinComponent {
    private val log by LoggerDelegate()
    private val retryPolicyService: RetryPolicyService by inject()
    private val feedClient: FeedClient by inject()
    private val taskService: TaskService by inject()

    @Throws(PostParseException::class)
    fun parse(): ThreadItem {
        log.debug("Parsing thread {}", threadId)
        taskService.increment()
        return try {
            parsePages()
        } catch (e: Exception) {
            throw PostParseException(e)
        } finally {
            taskService.decrement()
        }
    }

    @Throws(PostParseException::class)
    fun parseMetadata(): ThreadItem {
        log.debug("Parsing thread {} metadata", threadId)
        taskService.increment()
        return try {
            parsePage(since, mutableSetOf(), METADATA_LIMIT)
        } catch (e: Exception) {
            throw PostParseException(e)
        } finally {
            taskService.decrement()
        }
    }

    private fun parsePages(): ThreadItem =
        collectThreadPages(threadId, since) { pageSince, seenMainLinks ->
            parsePage(pageSince, seenMainLinks)
        }

    private fun parsePage(
        since: Long,
        seenMainLinks: MutableSet<String>,
        limit: Int = PAGE_LIMIT,
    ): ThreadItem {
        val parameters = buildMap {
            put("threadid", threadId.toString())
            put("limit", limit.toString())
            if (since > 0) {
                put("since", since.toString())
            }
        }
        return Failsafe.with(retryPolicyService.buildRetryPolicy<Any>("Failed to parse thread $threadId: ")).onFailure {
            log.error("Failed to process thread {} page since {}", threadId, since, it.exception)
        }.get(CheckedSupplier {
            val handler = ThreadLookupAPIResponseHandler(siteProxy, seenMainLinks, threadId)
            val feedResponse = feedClient.get(parameters)
            ByteArrayInputStream(feedResponse.body).use {
                factory.newSAXParser().parse(it, handler)
            }
            seenMainLinks.addAll(handler.seenLinks())
            handler.result
        })
    }

    companion object {
        private const val PAGE_LIMIT = 500
        private const val METADATA_LIMIT = 1
        private val factory = SAXParserFactory.newInstance()
    }
}

private const val MAX_THREAD_PAGES = 1_000

internal fun collectThreadPages(
    threadId: Long,
    since: Long,
    fetchPage: (Long, MutableSet<String>) -> ThreadItem,
): ThreadItem {
    val seenMainLinks = mutableSetOf<String>()
    val postItemList = mutableListOf<PostItem>()
    var currentSince = since
    var firstPage: ThreadItem? = null
    var lastPage: ThreadItem? = null
    var page = 0

    while (true) {
        val result = fetchPage(currentSince, seenMainLinks)
        if (result.error.isNotBlank()) {
            return if (lastPage == null) {
                result
            } else {
                lastPage.copy(
                    postItemList = postItemList.toList(),
                    error = result.error,
                    errorType = result.errorType,
                    pagerMore = false,
                    pagerPagePosts = postItemList.size,
                )
            }
        }

        if (firstPage == null) {
            firstPage = result
        }
        lastPage = result
        postItemList.addAll(result.postItemList)
        page++

        if (!result.pagerMore) {
            break
        }
        if (page >= MAX_THREAD_PAGES) {
            return result.copy(
                postItemList = postItemList.toList(),
                error = "Thread $threadId exceeded $MAX_THREAD_PAGES pages",
                errorType = "paging",
                pagerMore = false,
                pagerPagePosts = postItemList.size,
            )
        }
        if (result.pagerNextAfter <= currentSince) {
            return result.copy(
                postItemList = postItemList.toList(),
                error = "Paging did not advance past post $currentSince for thread $threadId",
                errorType = "paging",
                pagerMore = false,
                pagerPagePosts = postItemList.size,
            )
        }
        currentSince = result.pagerNextAfter
    }

    val first = firstPage ?: return lastPage!!
    val last = lastPage ?: return first
    return last.copy(
        postItemList = postItemList.toList(),
        summaryImages = postItemList.sumOf { it.imageCount },
        pagerAfter = first.pagerAfter,
        pagerNextAfter = 0,
        pagerMore = false,
        pagerPagePosts = postItemList.size,
    )
}
