package me.vripper.utilities

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

object ArchiveUtils {

    fun zipDirectory(sourceDir: Path) {
        // Validate that the input is a valid directory
        if (!Files.exists(sourceDir) || !sourceDir.isDirectory()) {
            throw IllegalArgumentException("The provided path is not a valid directory: ${sourceDir.toAbsolutePath()}")
        }

        // Determine the output zip file location and name
        val zipFile = sourceDir.parent.resolve("${sourceDir.name}.zip")

        // CHECK: Crash immediately if the ZIP file already exists
        if (Files.exists(zipFile)) {
            throw FileAlreadyExistsException(
                zipFile.toFile(),
                null,
                "A ZIP file with this name already exists in the target directory: $zipFile"
            )
        }

        // Open ZipOutputStream using NIO's Files.newOutputStream
        ZipOutputStream(Files.newOutputStream(zipFile)).use { zos ->
            // Walk through the file tree using Java NIO
            Files.walk(sourceDir).use { stream ->
                stream.forEach { path ->
                    // Process only regular files (implicitly skips the root directory itself)
                    if (path.isRegularFile()) {
                        // FIX: Removed "${sourceDir.name}/" prefix so files sit directly at the ZIP root
                        val relativePath = path.relativeTo(sourceDir).toString()

                        // Standardize separators to forward slashes for ZIP format compatibility
                        val entryName = relativePath.replace(File.separatorChar, '/')

                        zos.putNextEntry(ZipEntry(entryName))
                        Files.copy(path, zos)
                    }
                }
            }
        } // Closing the ZipOutputStream automatically finalizes the last remaining open entry.
    }
}