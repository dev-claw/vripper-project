package me.vripper.host

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.services.DownloadService.ImageDownloadContext
import me.vripper.utilities.ApplicationProperties.IMX_SUBDOMAINS
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.RequestLimit
import org.apache.hc.client5.http.classic.methods.HttpHead
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager
import java.util.concurrent.TimeUnit


internal class ImxHost : Host("imx.to", 8) {
    companion object {
        private val resolvedHosts: Cache<Long, String> =
            Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build()
    }

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
        synchronized(image.postId.toString().intern()) {
            val subDomain = resolvedHosts.getIfPresent(image.postId)
            if (subDomain != null) {
                val imgUrl = findPattern(image, subDomain)
                return Pair(
                    imgTitle.ifEmpty { getDefaultImageName(imgUrl) }, imgUrl
                )
            } else {
                IMX_SUBDOMAINS.forEach { subDomain ->
                    val imgUrl = findPattern(image, subDomain)
                    val result = runCatching {
                        RequestLimit.getPermit(1)
                        val httpHead = HttpHead(imgUrl)
                            .also {
                                it.setAbsoluteRequestUri(true)
                                context.requests.add(it)
                            }
                        log.info("{}", httpHead)
                        failFastHttpClient.execute(httpHead) { response ->
                            if (response.code / 100 != 2) {
                                throw HostException("Invalid response code ${response.code}")
                            }
                        }
                    }
                    if (result.isSuccess) {
                        resolvedHosts.put(image.postId, subDomain)
                        return Pair(
                            imgTitle.ifEmpty { getDefaultImageName(imgUrl) }, imgUrl
                        )
                    }
                }
            }
        }
        throw HostException("Unable to find full size image for ${image.url}")
    }

    private fun findPattern(image: ImageEntity, subDomain: String): String = image.thumbUrl
        .replace("http:", "https:")
        .replace("imx.to", subDomain)
        .replace("upload/small/", "i/")
        .replace("u/i/", "i/")
        .replace("u/t/", "i/")
        .replace("t/", "i/")
}