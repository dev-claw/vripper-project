package me.vripper.utilities

import me.vripper.entities.ImageEntity
import me.vripper.exception.RenameException
import me.vripper.model.Settings
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.walk


internal object PathUtils {
    private val log by LoggerDelegate()
    private val imageExtensions = listOf("bmp", "gif", "jpg", "jpeg", "png", "webp")


    fun calculateDownloadPath(
        forum: String, threadTitle: String, settings: Settings
    ): Path {
        var downloadDirectory = if (settings.downloadSettings.forumSubDirectory) Path.of(
            settings.downloadSettings.downloadPath, sanitize(forum)
        ) else Path.of(
            settings.downloadSettings.downloadPath
        )
        downloadDirectory =
            if (settings.downloadSettings.threadSubLocation) downloadDirectory.resolve(threadTitle) else downloadDirectory
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
        val sanitizedPath =
            path.replace("\\.|\\\\|/|\\||:|\\?|\\*|\"|<|>|\\p{Cntrl}".toRegex(), "_")
        log.debug(String.format("%s sanitized to %s", path, sanitizedPath))
        return sanitizedPath
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

    fun moveItem(source: Path, destinationDir: Path, overwrite: Boolean) {
        if (!Files.exists(source)) {
            throw IllegalArgumentException("Source path does not exist: $source")
        }

        val sourceName = source.fileName ?: throw IllegalArgumentException("Invalid source path")
        val targetPath = destinationDir.resolve(sourceName)

        // FAIL FAST: Root check
        if (Files.exists(targetPath) && !overwrite) {
            throw FileAlreadyExistsException("Target already exists and overwrite is set to false: $targetPath")
        }

        // Ensure the container directory exists
        Files.createDirectories(destinationDir)

        if (Files.isDirectory(source)) {
            moveFolderRecursively(source, targetPath, overwrite)
        } else {
            // If overwrite is true and target file exists, explicitly delete it first
            if (overwrite) {
                Files.deleteIfExists(targetPath)
            }
            // Move single file without StandardCopyOption.REPLACE_EXISTING to let OS enforce safety if something went wrong
            Files.move(source, targetPath)
        }
    }

    private fun moveFolderRecursively(source: Path, actualTargetRoot: Path, overwrite: Boolean) {
        source.walk().forEach { sourcePath ->
            val relativePath = source.relativize(sourcePath)
            val targetPath = actualTargetRoot.resolve(relativePath)

            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(targetPath)
            } else {
                Files.createDirectories(targetPath.parent)

                // STRICT NESTED CHECK: Respect the overwrite flag for every sub-file
                if (Files.exists(targetPath)) {
                    if (!overwrite) {
                        throw FileAlreadyExistsException("Nested file already exists and overwrite is false: $targetPath")
                    }
                    Files.delete(targetPath) // Explicit clean deletion before moving
                }

                Files.move(sourcePath, targetPath)
            }
        }
        source.toFile().deleteRecursively()
    }
}