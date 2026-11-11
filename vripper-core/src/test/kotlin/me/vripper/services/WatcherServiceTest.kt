package me.vripper.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WatcherServiceTest {

    @Test
    fun `runs immediately when never checked`() {
        assertEquals(0, WatcherService.watcherNextDelayMs(0, 900_000, 1_000_000))
    }

    @Test
    fun `waits for the remaining interval`() {
        val interval = 900_000L
        val now = 1_000_000L
        assertEquals(interval, WatcherService.watcherNextDelayMs(now, interval, now))
        assertEquals(300_000, WatcherService.watcherNextDelayMs(now - 600_000, interval, now))
        assertEquals(0, WatcherService.watcherNextDelayMs(now - 1_000_000, interval, now))
    }

    @Test
    fun `backs off exponentially up to six hours`() {
        val interval = 900_000L
        assertEquals(0, WatcherService.watcherBackoffMs(0, interval))
        assertEquals(1_800_000, WatcherService.watcherBackoffMs(1, interval))
        assertEquals(3_600_000, WatcherService.watcherBackoffMs(2, interval))
        assertEquals(6 * 60 * 60 * 1000L, WatcherService.watcherBackoffMs(20, interval))
    }
}
