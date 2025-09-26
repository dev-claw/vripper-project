package me.vripper.host

import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.services.DownloadService.ImageDownloadContext
import me.vripper.utilities.LoggerDelegate
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager
import java.util.concurrent.TimeUnit


internal class ImxHost : Host("imx.to", 8) {

    private val log by LoggerDelegate()

    private val failFastHttpClient = HttpClients.custom().apply {
        setConnectionManager(BasicHttpClientConnectionManager().apply {
            connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(5000, TimeUnit.MILLISECONDS)
                .setSocketTimeout(5000, TimeUnit.MILLISECONDS)
                .build()
        })
    }.build()

    @Throws(HostException::class)
    override fun resolve(
        image: ImageEntity,
        context: ImageDownloadContext
    ): Pair<String, String> {
        log.debug("Resolving name and image url for ${image.url}")
        val imgTitle = String.format("IMG_%04d", image.index + 1)
        val imgUrl = findPattern(image)
        return Pair(
            imgTitle.ifEmpty { getDefaultImageName(imgUrl) }, imgUrl
        )
    }

    private fun findPattern(image: ImageEntity): String = image.thumbUrl
        .replace("http:", "https:")
        .replace("upload/small/", "u/i/")
        .replace("u/t/", "u/i/")
}