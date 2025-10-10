package me.vripper.host

import me.vripper.exception.HostException
import me.vripper.exception.XpathException
import me.vripper.services.download.ImageDownloadRunnable
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.w3c.dom.Node

internal class PixRouteHost : Host("pixroute.com", 11) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        context: ImageDownloadRunnable.Context
    ): Pair<String, String> {
        val document = fetchDocument(context.imageEntity.url, context)
        val imgNode: Node = try {
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, context.imageEntity.url))
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                IMG_XPATH,
                context.imageEntity.url
            )
        )
        return try {
            log.debug(String.format("Resolving name and image url for %s", context.imageEntity.url))
            Pair(imgNode.attributes.getNamedItem("alt").textContent.trim { it <= ' ' },
                imgNode.attributes.getNamedItem("src").textContent.trim { it <= ' ' })
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val IMG_XPATH = "//img[@id='imgpreview']"
    }
}