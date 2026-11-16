package me.vripper.vgapi

import dev.failsafe.Failsafe
import dev.failsafe.function.CheckedSupplier
import me.vripper.exception.PostParseException
import me.vripper.services.RetryPolicyService
import me.vripper.services.TaskService
import me.vripper.utilities.LogContext
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

internal class PostLookupAPIParser(
    private val siteProxy: String,
    private val threadId: Long,
    private val postId: Long
) : KoinComponent {
    private val log by LoggerDelegate()
    private val retryPolicyService: RetryPolicyService by inject()
    private val feedClient: FeedClient by inject()
    private val taskService: TaskService by inject()

    @Throws(PostParseException::class)
    fun parse(): ThreadItem? = LogContext.withMdc(threadId = threadId, postId = postId) {
        log.debug("Parsing post $postId")
        val parameters = mapOf("postid" to postId.toString())
        taskService.increment()
        try {
            Failsafe.with(retryPolicyService.buildRetryPolicy<Any>("Failed to parse post $postId: ")).onFailure {
                log.error(
                    "Failed to process thread $threadId, post $postId", it.exception
                )
            }.get(CheckedSupplier {
                val handler = ThreadLookupAPIResponseHandler(siteProxy, expectedThreadId = threadId)
                val feedResponse = feedClient.get(parameters, threadId = threadId, postId = postId)
                ByteArrayInputStream(feedResponse.body).use {
                    factory.newSAXParser().parse(it, handler)
                    handler.result
                }
            })
        } catch (e: Exception) {
            throw PostParseException(e)
        } finally {
            taskService.decrement()
        }
    }

    companion object {
        private val factory = SAXParserFactory.newInstance()
    }
}