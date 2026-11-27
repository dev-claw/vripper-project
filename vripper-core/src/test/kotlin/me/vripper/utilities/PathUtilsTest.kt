package me.vripper.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
