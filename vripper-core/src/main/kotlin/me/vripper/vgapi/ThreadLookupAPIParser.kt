package me.vripper.vgapi

import dev.failsafe.Failsafe
import dev.failsafe.function.CheckedSupplier
import me.vripper.exception.DownloadException
import me.vripper.exception.PostParseException
import me.vripper.services.HTTPService
import me.vripper.services.RetryPolicyService
import me.vripper.services.VGAuthService
import me.vripper.tasks.Tasks
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.RequestLimit
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.net.URIBuilder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

internal class ThreadLookupAPIParser(private val threadId: Long) : KoinComponent {
    private val log by LoggerDelegate()
    private val cm: HTTPService by inject()
    private val retryPolicyService: RetryPolicyService by inject()
    private val vgAuthService: VGAuthService by inject()

    @Throws(PostParseException::class)
    fun parse(): ThreadItem {
        log.debug("Parsing thread $threadId")
        val httpGet =
            HttpGet(URIBuilder("https://viper.click/vr.php").also {
                it.setParameter(
                    "t",
                    threadId.toString()
                )
            }.build()).also { it.setAbsoluteRequestUri(true) }
        val threadLookupAPIResponseHandler = ThreadLookupAPIResponseHandler()
        Tasks.increment()
        return try {
            Failsafe.with(retryPolicyService.buildRetryPolicy<Any>("Failed to parse $httpGet: ")).onFailure {
                log.error(
                    "Failed to process thread $threadId",
                    it.exception
                )
            }.get(CheckedSupplier {
                RequestLimit.getPermit(1)
                log.info("{}", httpGet)
                cm.client.execute(
                    httpGet, vgAuthService.createClickContext()
                ) { response ->
                    if (response.code / 100 != 2) {
                        throw DownloadException("Unexpected response code '${response.code}' for $httpGet")
                    }
                    ByteArrayInputStream(EntityUtils.toByteArray(response.entity)).use {
                        factory.newSAXParser().parse(
                            it,
                            threadLookupAPIResponseHandler
                        )
                        threadLookupAPIResponseHandler.result
                    }
                }
            })
        } catch (e: Exception) {
            throw PostParseException(e)
        } finally {
            Tasks.decrement()
        }
    }

    companion object {
        private val factory = SAXParserFactory.newInstance()
    }
}