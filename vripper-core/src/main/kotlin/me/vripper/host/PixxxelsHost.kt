package me.vripper.host

import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.exception.XpathException
import me.vripper.services.DownloadService.ImageDownloadContext
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils

internal class PixxxelsHost : Host("pixxxels.cc", 12) {
    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        image: ImageEntity,
        context: ImageDownloadContext
    ): Pair<String, String> {
        val document = fetchDocument(image.url, context)
        val imgNode = try {
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, image.url))
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                IMG_XPATH,
                image.url
            )
        )
        val titleNode = try {
            log.debug(String.format("Looking for xpath expression %s in %s", TITLE_XPATH, image.url))
            XpathUtils.getAsNode(document, TITLE_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                TITLE_XPATH,
                image.url
            )
        )
        return try {
            log.debug(String.format("Resolving name and image url for %s", image.url))
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