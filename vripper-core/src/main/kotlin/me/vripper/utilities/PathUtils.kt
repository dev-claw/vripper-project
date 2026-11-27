package me.vripper.utilities

import me.vripper.entities.ImageEntity
import me.vripper.exception.RenameException
import me.vripper.model.Settings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries


internal object PathUtils {
    private val log by LoggerDelegate()
    private val imageExtensions = listOf("bmp", "gif", "jpg", "jpeg", "png", "webp")
    private val illegalCharacters = Regex("""[.\\/:*?"<>|\p{Cntrl}]""")
    private val windowsReservedNames: Set<String> = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL"))
        (1..9).forEach {
            add("COM$it")
            add("LPT$it")
        }
    }
    private const val MAX_COMPONENT_BYTES = 240


    fun calculateDownloadPath(
        forum: String, prefixId: String, threadTitle: String, settings: Settings
    ): Path {
        var downloadDirectory = Path.of(settings.downloadSettings.downloadPath)
        if (settings.downloadSettings.forumSubDirectory && forum.isNotBlank()) {
            downloadDirectory = downloadDirectory.resolve(sanitize(forum))
        }
        if (settings.downloadSettings.siteSubDirectory && prefixId.isNotBlank()) {
            downloadDirectory = downloadDirectory.resolve(sanitize(prefixId))
        }
        if (settings.downloadSettings.threadSubLocation && threadTitle.isNotBlank()) {
            downloadDirectory = downloadDirectory.resolve(sanitize(threadTitle))
        }
        return downloadDirectory
    }

    @Throws(RenameException::class)
    fun rename(
        imageEntityList: List<ImageEntity>,
        downloadDirectory: String,
        oldFolder: String,
        newFolder: String
    ) {
        val currentDownloadDirectory = Path(downloadDirectory, oldFolder)
        val newDownloadDirectory = Path(downloadDirectory, sanitize(newFolder))
        if (currentDownloadDirectory == newDownloadDirectory) {
            return
        }
        try {
            Files.createDirectories(newDownloadDirectory)
            imageEntityList.filter { it.filename.isNotBlank() }.forEach {
                Files.move(
                    currentDownloadDirectory.resolve(it.filename),
                    newDownloadDirectory.resolve(it.filename),
                    StandardCopyOption.ATOMIC_MOVE
                )
            }
            if (currentDownloadDirectory.listDirectoryEntries().isEmpty()) {
                try {
                    Files.delete(currentDownloadDirectory)
                } catch (e: Exception) {
                    log.error("Failed to delete directory $currentDownloadDirectory", e)
                }
            }
        } catch (e: IOException) {
            throw RenameException(
                String.format(
                    "Failed to move files from %s to %s", currentDownloadDirectory, newDownloadDirectory
                ),
                e
            )
        }
    }

    /**
     * Will sanitize the image name and remove extension
     *
     * @param path
     * @return Sanitized local path string
     */
    fun sanitize(path: String): String {
        var sanitizedPath = path.replace(illegalCharacters, "_")
            .trim()
            .trimEnd('.', ' ')
        if (sanitizedPath.isBlank()) {
            sanitizedPath = "_"
        }
        if (sanitizedPath.substringBefore('.').uppercase() in windowsReservedNames) {
            sanitizedPath = "_$sanitizedPath"
        }
        sanitizedPath = truncateToBytes(sanitizedPath, MAX_COMPONENT_BYTES)
            .trimEnd('.', ' ')
            .ifBlank { "_" }
        log.debug(String.format("%s sanitized to %s", path, sanitizedPath))
        return sanitizedPath
    }

    private fun truncateToBytes(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            return value
        }
        val result = StringBuilder()
        var byteCount = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val codePointBytes = when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            if (byteCount + codePointBytes > maxBytes) {
                break
            }
            result.appendCodePoint(codePoint)
            byteCount += codePointBytes
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    fun getExtension(fileName: String): String {
        val extension = if (fileName.contains(".")) fileName.substring(fileName.lastIndexOf(".") + 1) else ""
        return if (!imageExtensions.contains(extension.lowercase())) {
            ""
        } else {
            extension
        }
    }

    fun getFileNameWithoutExtension(fileName: String): String {
        return if (fileName.contains(".")) fileName.substring(
            0,
            fileName.lastIndexOf(".")
        ) else fileName
    }
}