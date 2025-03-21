package me.vripper.host

import me.vripper.exception.HostException
import me.vripper.exception.XpathException
import me.vripper.services.DataTransaction
import me.vripper.services.DownloadService.ImageDownloadContext
import me.vripper.services.DownloadSpeedService
import me.vripper.services.HTTPService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.w3c.dom.Document

internal class PixxxelsHost(
    httpService: HTTPService,
    dataTransaction: DataTransaction,
    downloadSpeedService: DownloadSpeedService,
) : Host("pixxxels.cc", 12, httpService, dataTransaction, downloadSpeedService) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        url: String,
        document: Document,
        context: ImageDownloadContext
    ): Pair<String, String> {
        val imgNode = try {
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url))
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                IMG_XPATH,
                url
            )
        )
        val titleNode = try {
            log.debug(String.format("Looking for xpath expression %s in %s", TITLE_XPATH, url))
            XpathUtils.getAsNode(document, TITLE_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                TITLE_XPATH,
                url
            )
        )
        return try {
            log.debug(String.format("Resolving name and image url for %s", url))
            val imgTitle = titleNode.textContent.trim { it <= ' ' }
            val imgUrl = imgNode.attributes.getNamedItem("href").textContent.trim { it <= ' ' }
            Pair(
                imgTitle.ifEmpty { imgUrl.substring(imgUrl.lastIndexOf('/') + 1) }, imgUrl
            )
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val IMG_XPATH = "//*[@id='download']"
        private const val TITLE_XPATH = "//*[contains(@class,'imagename')]"
    }
}