package me.vripper.host

import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.services.download.ImageDownloadRunnable
import me.vripper.utilities.HtmlUtils
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.core5.http.message.BasicNameValuePair


internal class ImxHost : Host("imx.to", 8) {

    private val log by LoggerDelegate()

    @Throws(HostException::class)
    override fun resolve(
        context: ImageDownloadRunnable.Context
    ): Pair<String, String> {
        log.debug("Resolving name and image url for ${context.imageEntity.url}")
        val imgTitle = getTitle(context).ifEmpty {
            getDefaultImageName(context.imageEntity.thumbUrl)
        }
        val imgUrl = findPattern(context.imageEntity)
        return Pair(
            imgTitle, imgUrl
        )
    }

    private fun getTitle(context: ImageDownloadRunnable.Context): String {
        return try {
            val httpsUrl = context.imageEntity.url.replace("http:", "https:")
            val document = fetchDocument(httpsUrl, context)
            var value: String? = null
            log.debug("Looking for xpath expression $CONTINUE_BUTTON_XPATH in $httpsUrl")
            val contDiv = XpathUtils.getAsNode(document, CONTINUE_BUTTON_XPATH)
                ?: throw HostException("$CONTINUE_BUTTON_XPATH cannot be found")
            val node = contDiv.attributes.getNamedItem("value")
            if (node != null) {
                value = node.textContent
            }
            log.debug("Click button found for $httpsUrl")
            val httpPost: HttpPost = HttpPost(httpsUrl).also {
                it.entity = UrlEncodedFormEntity(listOf(BasicNameValuePair("imgContinue", value)))
            }.also { context.requests.add(it) }
            log.debug("Requesting {}", httpPost)
            val doc = httpService.client.execute(
                httpPost, context.httpContext
            ) { response ->
                log.debug("Cleaning response for {}", httpPost)
                HtmlUtils.clean(response.entity.content)
            }

            log.debug("Looking for xpath expression $IMG_XPATH in $httpsUrl")
            val imgNode = XpathUtils.getAsNode(doc, IMG_XPATH)

            log.debug("Resolving name for $httpsUrl")
            val imgTitle = imgNode?.attributes?.getNamedItem("alt")?.textContent?.trim() ?: ""
            return imgTitle
        } catch (_: Exception) {
            ""
        }
    }

    private fun findPattern(image: ImageEntity): String {
        val url = image.thumbUrl
            .replace("http:", "https:")
        return if (url.startsWith("https://image.imx.to/u/t/")) {
            "https://image.imx.to/u/i/" + url.replace("https://image.imx.to/u/t/", "")
        } else if (url.startsWith("https://t.imx.to/t/")) {
            "https://image.imx.to/u/i/" + url.replace("https://t.imx.to/t/", "")
        } else if (url.startsWith("https://imx.to/upload/small/")) {
            "https://image.imx.to/u/i/" + url.replace("https://imx.to/upload/small/", "")
        } else {
            throw HostException("Cannot find pattern for url ${image.thumbUrl}")
        }
    }

    companion object {
        private const val CONTINUE_BUTTON_XPATH = "//*[@name='imgContinue']"
        private const val IMG_XPATH = "//img[@class='centred']"
    }
}