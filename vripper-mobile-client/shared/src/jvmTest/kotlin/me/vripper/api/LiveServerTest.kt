package me.vripper.api

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Optional end-to-end check against a throwaway server.
 *
 * Start a server with `grpc.enabled=true`, `grpc.passphrase=test` and run with
 * `VRIPPER_E2E_PORT=<port> ./gradlew :shared:jvmTest --tests '*LiveServerTest*'`.
 */
class LiveServerTest {
    @Test
    fun `connects and subscribes to live streams`() = runBlocking {
        val port = System.getenv("VRIPPER_E2E_PORT")?.toIntOrNull() ?: return@runBlocking
        val api = createEndpointApi("127.0.0.1", port, "test")

        try {
            val version = withTimeout(15_000) { api.getVersion() }
            val user = withTimeout(15_000) { api.loggedInUser() }
            val posts = withTimeout(15_000) { api.findAllPosts() }
            val queue = withTimeout(15_000) { api.getQueueState() }
            val threads = withTimeout(15_000) { api.findAllThreads() }
            val tasks = withTimeout(15_000) { api.findAllTasks() }
            val proxies = withTimeout(15_000) { api.getProxies() }
            val settings = withTimeout(15_000) { api.getSettings() }
            println(
                "connected: version=$version user='$user' posts=${posts.size} queue=$queue " +
                    "threads=${threads.size} tasks=${tasks.size} proxies=${proxies.size}",
            )
            println("settings: $settings")

            withTimeout(15_000) { api.startAllPosts() }
            withTimeout(15_000) { api.stopAllPosts() }
            val cleared = withTimeout(15_000) { api.clearCompletedPosts() }
            println("queue actions: cleared=$cleared")

            withTimeout(15_000) { api.saveSettings(settings) }
            val reloaded = withTimeout(15_000) { api.getSettings() }
            assertTrue(
                reloaded.hostSettings == settings.hostSettings,
                "host settings must be preserved when saving",
            )
            println("settings round-trip: download=${reloaded.download.downloadPath} hosts=${reloaded.hostSettings.size}")

            coroutineScope {
                val jobs = listOf(
                    launch { api.onNewPosts().collect { println("onNewPosts: ${it.id}") } },
                    launch { api.onUpdatePosts().collect { println("onUpdatePosts: ${it.id}") } },
                    launch { api.onDeletePosts().collect { println("onDeletePosts: $it") } },
                    launch { api.onUpdateMetadata().collect { println("onUpdateMetadata: ${it.postId}") } },
                    launch { api.onQueueStateUpdate().collect { println("onQueueStateUpdate: $it") } },
                    launch { api.onUpdateImages().collect { println("onUpdateImages: ${it.id}") } },
                    launch { api.onNewThread().collect { println("onNewThread: ${it.threadId}") } },
                    launch { api.onUpdateThread().collect { println("onUpdateThread: ${it.threadId}") } },
                    launch { api.onDeleteThread().collect { println("onDeleteThread: $it") } },
                    launch { api.onClearThreads().collect { println("onClearThreads") } },
                    launch { api.onNewTask().collect { println("onNewTask: ${it.name}") } },
                    launch { api.onUpdateTask().collect { println("onUpdateTask: ${it.name}") } },
                    launch { api.onClearTasks().collect { println("onClearTasks") } },
                    launch { api.onNewLog().collect { println("onNewLog: ${it.level} ${it.message}") } },
                    launch { api.onUpdateSettings().collect { println("onUpdateSettings") } },
                    launch { api.onDownloadSpeed().collect { println("onDownloadSpeed: $it") } },
                    launch { api.onVGUserUpdate().collect { println("onVGUserUpdate: '$it'") } },
                    launch { api.onErrorCountUpdate().collect { println("onErrorCountUpdate: $it") } },
                    launch { api.onTasksRunning().collect { println("onTasksRunning: $it") } },
                )
                // Subscribe first, then start draining the server log buffer.
                delay(500)
                withTimeout(15_000) { api.initLogger() }
                delay(14_500)
                jobs.forEach { it.cancel() }
            }
        } finally {
            api.close()
        }
    }
}
