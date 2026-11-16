package me.vripper.utilities

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class LogContextTest {

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun `withMdc sets and restores context`() {
        MDC.put(LogContext.THREAD_ID, "99")

        LogContext.withMdc(threadId = 12, postId = 34) {
            assertEquals("12", MDC.get(LogContext.THREAD_ID))
            assertEquals("34", MDC.get(LogContext.POST_ID))
        }

        assertEquals("99", MDC.get(LogContext.THREAD_ID))
        assertNull(MDC.get(LogContext.POST_ID))
    }

    @Test
    fun `withMdc clears missing ids inside the block`() {
        MDC.put(LogContext.THREAD_ID, "99")
        MDC.put(LogContext.POST_ID, "100")

        LogContext.withMdc(threadId = 12) {
            assertEquals("12", MDC.get(LogContext.THREAD_ID))
            assertNull(MDC.get(LogContext.POST_ID))
        }

        assertEquals("99", MDC.get(LogContext.THREAD_ID))
        assertEquals("100", MDC.get(LogContext.POST_ID))
    }
}
