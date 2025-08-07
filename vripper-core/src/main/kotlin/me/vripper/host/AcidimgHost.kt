package me.vripper.host

import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.exception.XpathException
import me.vripper.services.DownloadService.ImageDownloadContext
import me.vripper.services.HTTPService
import me.vripper.utilities.HtmlUtils
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.core5.http.message.BasicNameValuePair
import org.w3c.dom.Node

internal class AcidimgHost(
    private val httpService: HTTPService,
) : Host("acidimg.cc", 0) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        image: ImageEntity, context: ImageDownloadContext
    ): Pair<String, String> {
        val document = fetchDocument(image.url, context)
        try {
            log.debug(
                String.format(
                    "Looking for xpath expression %s in %s", CONTINUE_BUTTON_XPATH, image.url
                )
            )
            XpathUtils.getAsNode(document, CONTINUE_BUTTON_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        }
        log.debug(String.format("Click button found for %s", image.url))
        val httpPost = HttpPost(image.url).also {
            it.addHeader("Referer", image.url)
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
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, image.url))
            XpathUtils.getAsNode(doc, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'", IMG_XPATH, image.url
            )
        )
        return try {
            log.debug(String.format("Resolving name and image url for %s", image.url))
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