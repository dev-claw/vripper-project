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
import org.w3c.dom.Document
import org.w3c.dom.Node

internal class ImageVenueHost(
    httpService: HTTPService,
    dataTransaction: DataTransaction,
    downloadSpeedService: DownloadSpeedService,
) : Host("imagevenue.com", 4, httpService, dataTransaction, downloadSpeedService) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        url: String,
        document: Document,
        context: ImageDownloadContext
    ): Pair<String, String> {
        val doc = try {
            log.debug(
                String.format(
                    "Looking for xpath expression %s in %s",
                    CONTINUE_BUTTON_XPATH,
                    url
                )
            )
            if (XpathUtils.getAsNode(document, CONTINUE_BUTTON_XPATH) != null) {
                // Button detected. No need to actually click it, just make the call again.
                fetch(url, context) {
                    HtmlUtils.clean(it.entity.content)
                }
            } else {
                document
            }
        } catch (e: XpathException) {
            throw HostException(e)
        }
        val imgNode: Node = try {
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url))
            XpathUtils.getAsNode(doc, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                IMG_XPATH,
                url
            )
        )
        return try {
            log.debug(String.format("Resolving name and image url for %s", url))
            val imgTitle = imgNode.attributes.getNamedItem("alt").textContent.trim { it <= ' ' }
            val imgUrl = imgNode.attributes.getNamedItem("src").textContent.trim { it <= ' ' }
            Pair(
                if (imgTitle.isEmpty()) imgUrl.substring(imgUrl.lastIndexOf('/') + 1) else imgTitle,
                imgUrl
            )
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val CONTINUE_BUTTON_XPATH = "//a[@title='Continue to ImageVenue']"
        private const val IMG_XPATH = "//a[@data-toggle='full']/img[@id='main-image']"
    }
}