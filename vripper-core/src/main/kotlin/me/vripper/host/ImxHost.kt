package me.vripper.host

import me.vripper.exception.HostException
import me.vripper.exception.HtmlProcessorException
import me.vripper.exception.XpathException
import me.vripper.services.DataTransaction
import me.vripper.services.DownloadService.ImageDownloadContext
import me.vripper.services.DownloadSpeedService
import me.vripper.services.HTTPService
import me.vripper.utilities.HtmlUtils
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.core5.http.message.BasicNameValuePair
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.IOException

internal class ImxHost(
    private val httpService: HTTPService,
    dataTransaction: DataTransaction,
    downloadSpeedService: DownloadSpeedService,
) : Host("imx.to", 8, httpService, dataTransaction, downloadSpeedService) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        url: String,
        document: Document,
        context: ImageDownloadContext
    ): Pair<String, String> {
        val httpsUrl = url.replace("http:", "https:")
        var value: String? = null
        try {
            log.debug("Looking for xpath expression $CONTINUE_BUTTON_XPATH in $httpsUrl")
            val contDiv = XpathUtils.getAsNode(document, CONTINUE_BUTTON_XPATH)
                ?: throw HostException("$CONTINUE_BUTTON_XPATH cannot be found")
            val node = contDiv.attributes.getNamedItem("value")
            if (node != null) {
                value = node.textContent
            }
        } catch (e: XpathException) {
            throw HostException(e)
        }
        if (value == null) {
            throw HostException("Failed to obtain value attribute from continue input")
        }
        log.debug("Click button found for $httpsUrl")
        val httpPost: HttpPost = HttpPost(httpsUrl).also {
            it.entity = UrlEncodedFormEntity(listOf(BasicNameValuePair("imgContinue", value)))
        }.also { context.requests.add(it) }
        log.debug("Requesting {}", httpPost)
        val doc = try {
            httpService.client.execute(
                httpPost, context.httpContext
            ) { response ->
                log.debug("Cleaning response for {}", httpPost)
                HtmlUtils.clean(response.entity.content)
            }
        } catch (e: IOException) {
            throw HostException(e)
        } catch (e: HtmlProcessorException) {
            throw HostException(e)
        }
        val imgNode: Node = try {
            log.debug("Looking for xpath expression $IMG_XPATH in $httpsUrl")
            XpathUtils.getAsNode(doc, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            "Xpath $IMG_XPATH cannot be found in $httpsUrl"
        )
        return try {
            log.debug("Resolving name and image url for $httpsUrl")
            val imgTitle = imgNode.attributes.getNamedItem("alt").textContent.trim { it <= ' ' }
            val imgUrl = imgNode.attributes.getNamedItem("src").textContent.trim { it <= ' ' }
            Pair(
                imgTitle.ifEmpty { getDefaultImageName(imgUrl) }, imgUrl
            )
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val CONTINUE_BUTTON_XPATH = "//*[@name='imgContinue']"
        private const val IMG_XPATH = "//img[@class='centred']"
    }
}