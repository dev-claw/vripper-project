package me.vripper.download

import dev.failsafe.function.CheckedRunnable
import me.vripper.entities.ImageEntity
import me.vripper.entities.Status
import me.vripper.exception.DownloadException
import me.vripper.exception.HostException
import me.vripper.host.DownloadedImage
import me.vripper.host.Host
import me.vripper.host.ImageMimeType
import me.vripper.model.Settings
import me.vripper.services.DataTransaction
import me.vripper.services.VGAuthService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils.getExtension
import me.vripper.utilities.PathUtils.getFileNameWithoutExtension
import me.vripper.utilities.PathUtils.sanitize
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.pathString

internal class ImageDownloadRunnable(
    val imageEntity: ImageEntity, val postRank: Int, private val settings: Settings
) : KoinComponent, CheckedRunnable {
    private val log by LoggerDelegate()
    private val dataTransaction: DataTransaction by inject()
    private val vgauthService: VGAuthService by inject()
    private val hosts: List<Host> = getKoin().getAll()
    var completed = false
    var stopped = false

    private lateinit var context: ImageDownloadContext

    fun download() {
        try {
            imageEntity.status = Status.DOWNLOADING
            imageEntity.downloaded = 0
            dataTransaction.updateImage(imageEntity)
            synchronized(imageEntity.postId.toString().intern()) {
                val post = dataTransaction.findPostById(context.postId)
                if (post.status != Status.DOWNLOADING) {
                    post.status = Status.DOWNLOADING
                    dataTransaction.updatePost(post)
                    vgauthService.leaveThanks(post)
                }
            }
            log.debug("Getting image url and name from ${imageEntity.url} using ${imageEntity.host}")
            val host = hosts.first { it.isSupported(imageEntity.url) }
            val downloadedImage = host.downloadInternal(imageEntity.url, context)
            log.debug("Resolved name for ${imageEntity.url}: ${downloadedImage.name}")
            log.debug("Downloaded image {} to {}", imageEntity.url, downloadedImage.path)
            synchronized(imageEntity.postId.toString().intern()) {
                val post = dataTransaction.findPostById(context.postId)
                val downloadDirectory = Path(post.downloadDirectory, post.folderName).pathString
                checkImageTypeAndRename(
                    downloadDirectory, downloadedImage, imageEntity.index
                )
                if (imageEntity.downloaded == imageEntity.size && imageEntity.size > 0) {
                    imageEntity.status = Status.FINISHED
                    post.done += 1
                    post.downloaded += imageEntity.size
                    dataTransaction.updatePost(post)
                } else {
                    imageEntity.status = Status.ERROR
                }
                dataTransaction.updateImage(imageEntity)
            }
        } catch (e: Exception) {
            if (stopped) {
                return
            }
            imageEntity.status = Status.ERROR
            dataTransaction.updateImage(imageEntity)
            throw DownloadException(e)
        }
    }

    @Throws(HostException::class)
    private fun checkImageTypeAndRename(
        downloadDirectory: String, downloadedImage: DownloadedImage, index: Int
    ) {
        val existingExtension = getExtension(downloadedImage.name).lowercase()
        val fileNameWithoutExtension = getFileNameWithoutExtension(downloadedImage.name)
        val extension = when (downloadedImage.type) {
            ImageMimeType.IMAGE_BMP -> "BMP"
            ImageMimeType.IMAGE_GIF -> "GIF"
            ImageMimeType.IMAGE_JPEG -> "JPG"
            ImageMimeType.IMAGE_PNG -> "PNG"
            ImageMimeType.IMAGE_WEBP -> "WEBP"
        }
        val filename =
            if (existingExtension.isBlank()) "${sanitize(downloadedImage.name)}.$extension" else "${
                sanitize(
                    fileNameWithoutExtension
                )
            }.$extension"
        try {
            val downloadDestinationFolder = Path.of(downloadDirectory)
            Files.createDirectories(downloadDestinationFolder)
            val finalFilename = "${
                if (settings.downloadSettings.forceOrder) String.format(
                    "%03d_", index + 1
                ) else ""
            }$filename"
            imageEntity.filename = finalFilename
            val imageDownloadPath = downloadDestinationFolder.resolve(finalFilename)
            Files.copy(downloadedImage.path, imageDownloadPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            throw HostException("Failed to rename the image", e)
        } finally {
            try {
                Files.delete(downloadedImage.path)
            } catch (_: IOException) {
            }
        }
    }

    override fun run() {
        context = ImageDownloadContext(imageEntity, settings)
        try {
            if (stopped) {
                return
            }
            download()
        } finally {
            completed = true
            context.cancelCoroutines()
        }
    }

    fun stop() {
        stopped = true
        context.requests.forEach { it.abort() }
        context.cancelCoroutines()
        dataTransaction.updateImage(context.imageEntity)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ImageDownloadRunnable
        return imageEntity.id == that.imageEntity.id
    }

    override fun hashCode(): Int {
        return Objects.hash(imageEntity.id)
    }
}