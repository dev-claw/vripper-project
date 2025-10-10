package me.vripper.host

import me.vripper.exception.HostException
import me.vripper.exception.XpathException
import me.vripper.services.download.ImageDownloadRunnable
import me.vripper.utilities.HtmlUtils
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.w3c.dom.Node
import java.net.URI
import java.net.URISyntaxException

internal class PimpandhostHost : Host("pimpandhost.com", 9) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        context: ImageDownloadRunnable.Context
    ): Pair<String, String> {
        val newUrl: String
        try {
            newUrl = appendUri(
                context.imageEntity.url.replace("http://", "https://").replace("-medium(\\.html)?".toRegex(), ""),
                "size=original"
            )
        } catch (e: Exception) {
            throw HostException(e)
        }
        val doc = fetch(newUrl, context.imageEntity.url, context) {
            HtmlUtils.clean(it.entity.content)
        }
        val imgNode: Node = try {
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, newUrl))
            XpathUtils.getAsNode(doc, IMG_XPATH)
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
            log.debug(String.format("Resolving name and image url for %s", newUrl))
            val imgTitle = imgNode.attributes.getNamedItem("alt").textContent.trim { it <= ' ' }
            val imgUrl =
                "https:" + imgNode.attributes.getNamedItem("src").textContent.trim { it <= ' ' }
            Pair(
                imgTitle.ifEmpty { imgUrl.substring(imgUrl.lastIndexOf('/') + 1) }, imgUrl
            )
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    @Throws(URISyntaxException::class)
    fun appendUri(uri: String, appendQuery: String): String {
        val oldUri = URI(uri)
        var newQuery = oldUri.query
        if (newQuery == null) {
            newQuery = appendQuery
        } else {
            newQuery += "&$appendQuery"
        }
        return URI(
            oldUri.scheme, oldUri.authority, oldUri.path, newQuery, oldUri.fragment
        ).toString()
    }

    companion object {
        private const val IMG_XPATH = "//img[contains(@class, 'original')]"
    }
}
