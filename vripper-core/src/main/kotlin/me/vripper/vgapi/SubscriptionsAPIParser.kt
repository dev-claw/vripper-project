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

internal class SubscriptionsAPIParser(private val siteProxy: String) : KoinComponent {
    private val log by LoggerDelegate()
    private val retryPolicyService: RetryPolicyService by inject()
    private val feedClient: FeedClient by inject()
    private val taskService: TaskService by inject()

    @Throws(PostParseException::class)
    fun parse(): SubscriptionsItem {
        log.debug("Parsing subscriptions")
        val parameters = mapOf("subscriptions" to "1")
        taskService.increment()
        return try {
            Failsafe.with(retryPolicyService.buildRetryPolicy<Any>("Failed to parse subscriptions: ")).onFailure {
                log.error("Failed to process subscriptions", it.exception)
            }.get(CheckedSupplier {
                val handler = SubscriptionsAPIResponseHandler(siteProxy)
                val feedResponse = feedClient.get(parameters)
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
