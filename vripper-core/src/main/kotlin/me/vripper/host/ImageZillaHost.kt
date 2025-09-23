package me.vripper.host

import me.vripper.exception.HostException
import me.vripper.exception.XpathException
import me.vripper.services.download.ImageDownloadRunnable
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils

internal class ImageZillaHost : Host("imagezilla.net", 5) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        context: ImageDownloadRunnable.Context
    ): Pair<String, String> {
        val document = fetchDocument(context.imageEntity.url, context)
        val titleNode = try {
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
        log.debug(String.format("Resolving name for %s", context.imageEntity.url))
        var title = titleNode.attributes.getNamedItem("title").textContent.trim()
        titleNode.textContent.trim()
        if (title.isEmpty()) {
            title = getDefaultImageName(context.imageEntity.url)
        }
        return try {
            Pair(title, context.imageEntity.url.replace("show", "images"))
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val IMG_XPATH = "//img[@id='photo']"
    }
}