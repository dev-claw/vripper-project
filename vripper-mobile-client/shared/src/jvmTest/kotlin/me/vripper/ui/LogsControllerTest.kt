package me.vripper.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import me.vripper.data.LogEntry
import me.vripper.data.Settings

class LogsControllerTest {
    @Test
    fun `logs with a shared server sequence get unique client ids`() = runBlocking {
        val flow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 16)
        val api = object : FakeEndpointApi() {
            override fun onNewLog(): Flow<LogEntry> = flow
            override suspend fun initLogger() = Unit
            override suspend fun getSettings(): Settings = Settings()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = LogsController(api, scope)

        repeat(3) { index ->
            flow.emit(
                LogEntry(
                    uid = 0,
                    sequence = 0,
                    timestamp = "t$index",
                    threadName = "thread",
                    loggerName = "logger",
                    level = "INFO",
                    message = "message $index",
                    throwable = "",
                ),
            )
        }

        val logs = controller.logs.value
        assertEquals(3, logs.size)
        // This is the regression: the server leaves `sequence` at 0 for every entry,
        // so keys derived from it collided in the LazyColumn ("Key 0 already used").
        assertEquals(3, logs.map { it.uid }.toSet().size)
        scope.cancel()
    }
}
