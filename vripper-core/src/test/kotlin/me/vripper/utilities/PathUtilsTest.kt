package me.vripper.utilities

import me.vripper.entities.ImageEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PathUtilsTest {

    @Test
    fun `sanitize replaces illegal characters`() {
        assertEquals("a_b_c_d_e_f_g_h_i_j_k", PathUtils.sanitize("a/b\\c:d*e?f\"g<h>i|j.k"))
    }

    @Test
    fun `sanitize replaces control characters`() {
        assertEquals("a_b_c", PathUtils.sanitize("a\nb\tc"))
    }

    @Test
    fun `sanitize returns fallback for blank names`() {
        assertEquals("_", PathUtils.sanitize(""))
        assertEquals("_", PathUtils.sanitize("   "))
    }

    @Test
    fun `sanitize trims trailing whitespace`() {
        assertEquals("name", PathUtils.sanitize("name   "))
    }

    @Test
    fun `sanitize avoids windows reserved names`() {
        assertEquals("_CON", PathUtils.sanitize("CON"))
        assertEquals("_nul", PathUtils.sanitize("nul"))
        assertEquals("_COM1", PathUtils.sanitize("COM1"))
        assertEquals("_LPT9", PathUtils.sanitize("LPT9"))
        assertEquals("CONSOLE", PathUtils.sanitize("CONSOLE"))
    }

    @Test
    fun `sanitize limits ascii names to the maximum component size`() {
        val sanitized = PathUtils.sanitize("a".repeat(300))

        assertEquals(240, sanitized.length)
    }

    @Test
    fun `sanitize limits multi byte names by byte size`() {
        val sanitized = PathUtils.sanitize("é".repeat(200))

        assertEquals(120, sanitized.length)
        assertEquals(240, sanitized.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `sanitize does not split surrogate pairs when truncating`() {
        val sanitized = PathUtils.sanitize("😀".repeat(100))

        assertEquals(60, sanitized.codePointCount(0, sanitized.length))
        assertTrue(sanitized.toByteArray(Charsets.UTF_8).size <= 240)
    }

    @Test
    fun `rename changes directory case on disk`(@TempDir tempDir: Path) {
        val downloadDirectory = Files.createDirectories(tempDir.resolve("downloads"))
        val oldDirectory = Files.createDirectories(downloadDirectory.resolve("gallery"))
        Files.writeString(oldDirectory.resolve("image.jpg"), "image")

        PathUtils.rename(listOf(image("image.jpg")), downloadDirectory.toString(), "gallery", "Gallery")

        assertEquals(listOf("Gallery"), downloadDirectory.toFile().list()?.toList())
        assertEquals("image", Files.readString(downloadDirectory.resolve("Gallery").resolve("image.jpg")))
    }

    @Test
    fun `rename moves files to a new directory`(@TempDir tempDir: Path) {
        val downloadDirectory = Files.createDirectories(tempDir.resolve("downloads"))
        val oldDirectory = Files.createDirectories(downloadDirectory.resolve("gallery"))
        Files.writeString(oldDirectory.resolve("image.jpg"), "image")

        PathUtils.rename(listOf(image("image.jpg")), downloadDirectory.toString(), "gallery", "new-gallery")

        assertFalse(Files.exists(oldDirectory))
        assertEquals("image", Files.readString(downloadDirectory.resolve("new-gallery").resolve("image.jpg")))
    }

    @Test
    fun `rename merges into an existing case-different directory`(@TempDir tempDir: Path) {
        val downloadDirectory = Files.createDirectories(tempDir.resolve("downloads"))
        val oldDirectory = Files.createDirectories(downloadDirectory.resolve("gallery"))
        val existingDirectory = Files.createDirectories(downloadDirectory.resolve("Gallery"))
        assumeFalse(Files.isSameFile(oldDirectory, existingDirectory))
        Files.writeString(oldDirectory.resolve("old.jpg"), "old")
        Files.writeString(existingDirectory.resolve("existing.jpg"), "existing")

        PathUtils.rename(listOf(image("old.jpg")), downloadDirectory.toString(), "gallery", "Gallery")

        assertFalse(Files.exists(oldDirectory))
        assertEquals("old", Files.readString(existingDirectory.resolve("old.jpg")))
        assertEquals("existing", Files.readString(existingDirectory.resolve("existing.jpg")))
    }

    @Test
    fun `rename does nothing when the name is unchanged`(@TempDir tempDir: Path) {
        val downloadDirectory = Files.createDirectories(tempDir.resolve("downloads"))
        val oldDirectory = Files.createDirectories(downloadDirectory.resolve("gallery"))
        Files.writeString(oldDirectory.resolve("image.jpg"), "image")

        PathUtils.rename(listOf(image("image.jpg")), downloadDirectory.toString(), "gallery", "gallery")

        assertEquals("image", Files.readString(oldDirectory.resolve("image.jpg")))
    }

    private fun image(filename: String) = ImageEntity(
        url = "https://example.com/$filename",
        thumbUrl = "",
        host = 0,
        index = 0,
        filename = filename,
    )
}
