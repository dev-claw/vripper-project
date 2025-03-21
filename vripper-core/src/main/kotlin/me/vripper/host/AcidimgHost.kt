package me.vripper.host

import me.vripper.exception.HostException
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

internal class AcidimgHost(
    private val httpService: HTTPService,
    dataTransaction: DataTransaction,
    downloadSpeedService: DownloadSpeedService,
) : Host("acidimg.cc", 0, httpService, dataTransaction, downloadSpeedService) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        url: String, document: Document, context: ImageDownloadContext
    ): Pair<String, String> {
        try {
            log.debug(
                String.format(
                    "Looking for xpath expression %s in %s", CONTINUE_BUTTON_XPATH, url
                )
            )
            XpathUtils.getAsNode(document, CONTINUE_BUTTON_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        }
        log.debug(String.format("Click button found for %s", url))
        val httpPost = HttpPost(url).also {
            it.addHeader("Referer", url)
            it.entity = UrlEncodedFormEntity(
                listOf(
                    BasicNameValuePair(
                        "imgContinue",
                        "Continue to your image"
                    )
                )
            )
        }.also { context.requests.add(it) }
        log.debug(String.format("Requesting %s", httpPost.uri))
        val doc = try {
            httpService.client.execute(
                httpPost, context.httpContext
            )
            { response ->
                log.debug(String.format("Cleaning response for %s", httpPost))
                HtmlUtils.clean(response.entity.content)
            }
        } catch (e: Exception) {
            throw HostException(e)
        }
        val imgNode: Node = try {
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url))
            XpathUtils.getAsNode(doc, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'", IMG_XPATH, url
            )
        )
        return try {
            log.debug(String.format("Resolving name and image url for %s", url))
            val imgTitle = imgNode.attributes.getNamedItem("alt").textContent.trim()
            val imgUrl = imgNode.attributes.getNamedItem("src").textContent.trim()
            Pair(
                imgTitle.ifEmpty { getDefaultImageName(imgUrl) }, imgUrl
            )
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val CONTINUE_BUTTON_XPATH = "//input[@id='continuebutton']"
        private const val IMG_XPATH = "//img[@class='centred']"
    }
}