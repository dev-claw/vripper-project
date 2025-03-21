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
import org.w3c.dom.Node

internal class TurboImageHost(
    httpService: HTTPService,
    dataTransaction: DataTransaction,
    downloadSpeedService: DownloadSpeedService,
) : Host("turboimagehost.com", 14, httpService, dataTransaction, downloadSpeedService) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        url: String,
        document: Document,
        context: ImageDownloadContext
    ): Pair<String, String> {
        var title: String?
        title = try {
            log.debug(String.format("Looking for xpath expression %s in %s", TITLE_XPATH, url))
            val titleNode: Node? = XpathUtils.getAsNode(document, TITLE_XPATH)
            log.debug(String.format("Resolving name for %s", url))
            titleNode?.textContent?.trim { it <= ' ' }
        } catch (e: XpathException) {
            throw HostException(e)
        }
        if (title.isNullOrEmpty()) {
            title = getDefaultImageName(url)
        }
        val urlNode: Node = XpathUtils.getAsNode(document, IMG_XPATH)
            ?: throw HostException(
                String.format(
                    "Xpath '%s' cannot be found in '%s'",
                    IMG_XPATH,
                    url
                )
            )
        return Pair(title, urlNode.attributes.getNamedItem("src").textContent.trim { it <= ' ' })
    }

    companion object {
        private const val TITLE_XPATH = "//div[contains(@class,'titleFullS')]/h1"
        private const val IMG_XPATH = "//img[@id='imageid']"
    }
}