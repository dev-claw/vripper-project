package me.vripper.host

import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.exception.XpathException
import me.vripper.services.DownloadService.ImageDownloadContext
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.w3c.dom.Node

internal class TurboImageHost : Host("turboimagehost.com", 14) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        image: ImageEntity,
        context: ImageDownloadContext
    ): Pair<String, String> {
        val document = fetchDocument(image.url, context)
        var title: String?
        title = try {
            log.debug(String.format("Looking for xpath expression %s in %s", TITLE_XPATH, image.url))
            val titleNode: Node? = XpathUtils.getAsNode(document, TITLE_XPATH)
            log.debug(String.format("Resolving name for %s", image.url))
            titleNode?.textContent?.trim { it <= ' ' }
        } catch (e: XpathException) {
            throw HostException(e)
        }
        if (title.isNullOrEmpty()) {
            title = getDefaultImageName(image.url)
        }
        val urlNode: Node = XpathUtils.getAsNode(document, IMG_XPATH)
            ?: throw HostException(
                String.format(
                    "Xpath '%s' cannot be found in '%s'",
                    IMG_XPATH,
                    image.url
                )
            )
        return Pair(title, urlNode.attributes.getNamedItem("src").textContent.trim { it <= ' ' })
    }

    companion object {
        private const val TITLE_XPATH = "//div[contains(@class,'titleFullS')]/h1"
        private const val IMG_XPATH = "//img[@id='imageid']"
    }
}