package me.vripper.host

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import me.vripper.exception.DownloadException
import me.vripper.exception.HostException
import me.vripper.services.DataAccessService
import me.vripper.services.DownloadSpeedService
import me.vripper.services.HTTPService
import me.vripper.services.download.ImageDownloadRunnable.Context
import me.vripper.utilities.HtmlUtils
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils.getFileNameWithoutExtension
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpHead
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.Header
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.Document
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path

internal abstract class Host(
    val hostName: String,
    val hostId: Byte,
) : KoinComponent {
    private val log by LoggerDelegate()

    private val httpService: HTTPService by inject()
    private val dataAccessService: DataAccessService by inject()
    private val downloadSpeedService: DownloadSpeedService by inject()

    companion object {
        private const val READ_BUFFER_SIZE = 8192
        private val hosts: MutableMap<String, Byte> = mutableMapOf()

        fun getHosts(): Map<String, Byte> {
            return hosts.toMap()
        }
    }

    init {
        hosts[hostName] = hostId
    }

    abstract fun resolve(
        context: Context
    ): Pair<String, String>

    fun downloadInternal(context: Context): DownloadedImage {
        if (hostId == 8.toByte()) {
            return downloadByHost(context)
        }
        val headers = head(context)
        // is the body of type image ?
        val imageMimeType = getImageMimeType(headers)
        val downloadedImage = if (imageMimeType != null) {
            // a direct link, awesome
            val downloadedImage = fetch(context.imageEntity.url, context.imageEntity.url, context) {
                handleImageDownload(it, context)
            }
            DownloadedImage(getDefaultImageName(context.imageEntity.url), downloadedImage.first, downloadedImage.second)
        } else {
            // linked image ?
            val value = headers.find { it.name.contains("content-type", true) }?.value
            if (value != null) {
                if (value.contains("text/html")) {
                    downloadByHost(context)
                } else {
                    throw HostException("Unable to download ${context.imageEntity.url}, can't process content type $value")
                }
            } else {
                throw HostException("Unexpected server response for ${context.imageEntity.url}, response have no content type")
            }
        }
        return downloadedImage
    }

    private fun downloadByHost(context: Context): DownloadedImage {
        val resolvedImage = resolve(context)
        val downloadImage: Pair<Path, ImageMimeType> =
            fetch(resolvedImage.second, context.imageEntity.url, context) {
                handleImageDownload(it, context)
            }
        return DownloadedImage(resolvedImage.first, downloadImage.first, downloadImage.second)
    }

    private fun handleImageDownload(
        response: ClassicHttpResponse,
        context: Context
    ): Pair<Path, ImageMimeType> {
        val mimeType = getImageMimeType(response.headers)
            ?: throw HostException("Unsupported image type ${response.getFirstHeader("content-type")}")

        val tempImage = Files.createTempFile(
            Path.of(context.settings.systemSettings.tempPath),
            "vripper_",
            ".tmp"
        )
        return BufferedOutputStream(Files.newOutputStream(tempImage)).use { bos ->
            synchronized(context.imageEntity.postEntityId.toString().intern()) {
                val post = dataAccessService.findPostByEntityId(context.imageEntity.postEntityId)
                val size = if (context.imageEntity.size < 0) {
                    response.entity.contentLength
                } else {
                    0
                }
                context.imageEntity.size = response.entity.contentLength
                post.size += size
                transaction {
                    dataAccessService.updateImage(context.imageEntity)
                    dataAccessService.updatePost(post)
                }
            }
            log.debug(
                "Length is ${context.imageEntity.size}"
            )
            log.debug(
                "Starting data transfer"
            )
            val buffer = ByteArray(READ_BUFFER_SIZE)
            var read: Int
            val reporterJob = context.launchCoroutine {
                while (isActive) {
                    dataAccessService.updateImage(context.imageEntity, false)
                    delay(100)
                }
            }
            while (response.entity.content.read(buffer)
                    .also { read = it } != -1
            ) {
                bos.write(buffer, 0, read)
                context.imageEntity.downloaded += read
                downloadSpeedService.reportDownloadedBytes(read.toLong())
            }
            runBlocking {
                reporterJob.cancelAndJoin()
            }
            dataAccessService.updateImage(context.imageEntity)
            Pair(tempImage, mimeType)
        }
    }

    fun isSupported(url: String): Boolean {
        return url.contains(hostName)
    }

    fun head(context: Context): Array<Header> {
        val httpHead = HttpHead(context.imageEntity.url).also {
            it.setAbsoluteRequestUri(true)
            context.requests.add(it)
        }
        log.info("{}", httpHead)
        return httpService.client.execute(
            httpHead,
            context.httpContext
        ) {
            if (it.code / 100 != 2) {
                throw HostException("Unexpected response code: ${it.code}")
            }
            it.headers
        }
    }

    fun <T> fetch(
        url: String,
        referer: String,
        context: Context,
        transformer: (ClassicHttpResponse) -> T
    ): T {
        val httpGet =
            HttpGet(url).also {
                it.addHeader("Referer", referer)
                it.setAbsoluteRequestUri(true)
            }.also { context.requests.add(it) }
        log.info("{}", httpGet)
        return httpService.client.execute(httpGet, context.httpContext) {
            if (it.code / 100 != 2) {
                throw DownloadException("Server returned code ${it.code}")
            }
            transformer(it)
        }
    }

    fun fetchDocument(
        url: String,
        context: Context
    ): Document {
        return fetch(url, url, context) {
            HtmlUtils.clean(it.entity.content)
        }.also {
            if (log.isDebugEnabled) {
                log.debug("Cleaning {} response", url)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getImageMimeType(headers: Array<Header>): ImageMimeType? {

        // first check if content type header exists
        val value = headers.find { it.name.contains("content-type", true) }?.value

        // header found, check the type
        return if (value != null) {
            ImageMimeType.entries.find {
                value.contains(it.strValue, true)
            }
        } else {
            null
        }
    }

    fun getDefaultImageName(imgUrl: String): String {
        val imageTitle = imgUrl.substring(imgUrl.lastIndexOf('/') + 1)
        log.debug(String.format("Extracting name from url %s: %s", imgUrl, imageTitle))
        return getFileNameWithoutExtension(imageTitle)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Host

        return hostName == other.hostName
    }

    override fun hashCode(): Int {
        return hostName.hashCode()
    }

    override fun toString(): String {
        return hostName
    }
}

data class DownloadedImage(val name: String, val path: Path, val type: ImageMimeType)

enum class ImageMimeType(val strValue: String) {
    IMAGE_BMP("image/bmp"),
    IMAGE_GIF("image/gif"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    IMAGE_WEBP("image/webp"),
}