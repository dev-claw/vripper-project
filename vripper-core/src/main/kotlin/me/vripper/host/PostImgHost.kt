package me.vripper.host

import me.vripper.exception.HostException
import me.vripper.services.download.ImageDownloadRunnable
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils

internal class PostImgHost : Host("postimg.cc", 13) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        context: ImageDownloadRunnable.Context
    ): Pair<String, String> {
        val document = fetchDocument(context.imageEntity.url.replace("http:", "https:"), context)
        log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, context.imageEntity.url))
        val node = XpathUtils.getAsNode(document, IMG_XPATH) ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                IMG_XPATH,
                context.imageEntity.url
            )
        )
        return Pair(
            node.attributes.getNamedItem("alt").textContent.trim(),
            node.attributes.getNamedItem("src").textContent.trim()
        )
    }

    companion object {
        private const val IMG_XPATH = "//img[contains(@class,'img-fluid')]"
    }
}