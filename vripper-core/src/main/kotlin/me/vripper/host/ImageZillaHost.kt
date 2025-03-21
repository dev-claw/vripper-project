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

internal class ImageZillaHost(
    httpService: HTTPService,
    dataTransaction: DataTransaction,
    downloadSpeedService: DownloadSpeedService,
) : Host("imagezilla.net", 5, httpService, dataTransaction, downloadSpeedService) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        url: String,
        document: Document,
        context: ImageDownloadContext
    ): Pair<String, String> {
        val titleNode = try {
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
        log.debug(String.format("Resolving name for %s", url))
        var title = titleNode.attributes.getNamedItem("title").textContent.trim()
        titleNode.textContent.trim()
        if (title.isEmpty()) {
            title = getDefaultImageName(url)
        }
        return try {
            Pair(title, url.replace("show", "images"))
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val IMG_XPATH = "//img[@id='photo']"
    }
}