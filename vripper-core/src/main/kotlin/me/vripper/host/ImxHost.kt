package me.vripper.host

import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.services.download.ImageDownloadRunnable
import me.vripper.utilities.LoggerDelegate


internal class ImxHost : Host("imx.to", 8) {

    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        context: ImageDownloadRunnable.Context
    ): Pair<String, String> {
        log.debug("Resolving name and image url for ${context.imageEntity.url}")
        val imgTitle = String.format("IMG_%04d", context.imageEntity.index + 1)
        val imgUrl = findPattern(context.imageEntity)
        return Pair(
            imgTitle.ifEmpty { getDefaultImageName(imgUrl) }, imgUrl
        )
    }

    private fun findPattern(image: ImageEntity): String = image.thumbUrl
        .replace("http:", "https:")
        .replace("upload/small/", "u/i/")
        .replace("u/t/", "u/i/")
}