package me.vripper.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class VripperIconsTest {
    @Test
    fun `all icons parse and expose a 24dp viewport`() {
        val icons = listOf(
            VripperIcons.Posts,
            VripperIcons.Threads,
            VripperIcons.Tasks,
            VripperIcons.Logs,
            VripperIcons.Settings,
            VripperIcons.Back,
            VripperIcons.Play,
            VripperIcons.Stop,
            VripperIcons.Refresh,
            VripperIcons.Add,
            VripperIcons.Search,
            VripperIcons.Delete,
            VripperIcons.ChevronLeft,
            VripperIcons.ChevronRight,
            VripperIcons.Image,
            VripperIcons.Warning,
            VripperIcons.More,
            VripperIcons.Close,
            VripperIcons.Link,
        )
        icons.forEach { icon ->
            assertEquals(24f, icon.viewportWidth, icon.name)
            assertEquals(24f, icon.viewportHeight, icon.name)
            assertEquals(1, icon.root.size, icon.name)
        }
    }
}
