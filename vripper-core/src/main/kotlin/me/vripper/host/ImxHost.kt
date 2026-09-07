package me.vripper.host

import me.vripper.entities.ImageEntity
import me.vripper.exception.HostException
import me.vripper.model.HostName
import me.vripper.model.HostSettingKey
import me.vripper.services.download.ImageDownloadRunnable
import me.vripper.utilities.HtmlUtils
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.XpathUtils
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.core5.http.message.BasicNameValuePair


internal class ImxHost : Host("imx", listOf("imx.to"), 8) {

    private val log by LoggerDelegate()

    data class ImxLink(val pageUrl: String, val thumbUrl: String, val imageUrl: String)

    @Throws(HostException::class)
    override fun resolve(
        context: ImageDownloadRunnable.Context
    ): Pair<String, String> {
        log.debug("Resolving name and image url for ${context.imageEntity.url}")
        val imxLink = findPattern(context.imageEntity)
        val imgTitle = getTitle(imxLink.pageUrl, context)
        return Pair(
            imgTitle, imxLink.imageUrl
        )
    }

    private fun getTitle(pageUrl: String, context: ImageDownloadRunnable.Context): String {

        return if (context.settings.hostSettings[HostName.IMX]?.get(HostSettingKey.TRY_TO_FETCH_ORIGINAL_FILENAME)
                .toBoolean()
        ) {
            val document = fetchDocument(pageUrl, context)
            var value: String? = null
            log.debug("Looking for xpath expression $CONTINUE_BUTTON_XPATH in $pageUrl")
            val contDiv = XpathUtils.getAsNode(document, CONTINUE_BUTTON_XPATH)
                ?: throw HostException("$CONTINUE_BUTTON_XPATH cannot be found")
            val node = contDiv.attributes.getNamedItem("value")
            if (node != null) {
                value = node.textContent
            }
            log.debug("Click button found for $pageUrl")
            val httpPost: HttpPost = HttpPost(pageUrl).also {
                it.entity = UrlEncodedFormEntity(listOf(BasicNameValuePair("imgContinue", value)))
            }.also { context.requests.add(it) }
            log.debug("Requesting {}", httpPost)
            val doc = httpService.client.execute(
                httpPost, context.httpContext
            ) { response ->
                log.debug("Cleaning response for {}", httpPost)
                HtmlUtils.clean(response.entity.content)
            }

            log.debug("Looking for xpath expression $IMG_XPATH in $pageUrl")
            val imgNode = XpathUtils.getAsNode(doc, IMG_XPATH)

            log.debug("Resolving name for $pageUrl")
            val imgTitle = imgNode?.attributes?.getNamedItem("alt")?.textContent?.trim() ?: ""
            imgTitle

        } else {
            getDefaultImageName(context.imageEntity.thumbUrl)
        }
    }

    private fun findPattern(image: ImageEntity): ImxLink {
        val url = image.thumbUrl
            .replace("http:", "https:")
        return if (url.startsWith("https://image.imx.to/u/t/")) {
            ImxLink(
                "https://imx.to/i/" + extractIdFromUrl(url),
                url,
                "https://image.imx.to/u/i/" + url.replace("https://image.imx.to/u/t/", "")
            )
        } else if (url.startsWith("https://imx.to/u/t")) {
            ImxLink(
                "https://imx.to/i/" + extractIdFromUrl(url),
                url,
                "https://image.imx.to/u/i/" + url.replace("https://imx.to/u/t", "")
            )
        } else if (url.startsWith("https://t.imx.to/t/")) {
            ImxLink(
                "https://imx.to/i/" + extractIdFromUrl(url),
                url,
                "https://image.imx.to/u/i/" + url.replace("https://t.imx.to/t/", "")
            )
        } else if (url.startsWith("https://imx.to/upload/small/")) {
            ImxLink(
                "https://imx.to/i/" + extractIdFromUrl(url),
                url,
                "https://image.imx.to/u/i/" + url.replace("https://imx.to/upload/small/", "")
            )
        } else if (url.startsWith("https://i.imx.to/t/")) {
            ImxLink(
                "https://imx.to/i/" + extractIdFromUrl(url),
                url,
                "https://image.imx.to/u/i/" + url.replace("https://i.imx.to/t/", "")
            )
        } else if (url.startsWith("https://image.imx.to/u/i/")) {
            ImxLink("https://imx.to/i/" + extractIdFromUrl(url), url.replace("/u/i", "/u/t"), url)
        } else {
            throw HostException("Cannot find pattern for url ${image.thumbUrl}")
        }
    }

    fun extractIdFromUrl(url: String): String? {
        // Regex matches the string between the last slash '/' and the dot '.' before the extension
        val regex = """/([^/\s]+)\.[a-zA-Z0-9]+$""".toRegex()
        val matchResult = regex.find(url)

        return matchResult?.groupValues?.get(1)
    }

    companion object {
        private const val CONTINUE_BUTTON_XPATH = "//*[@name='imgContinue']"
        private const val IMG_XPATH = "//img[@class='centred']"
    }
}