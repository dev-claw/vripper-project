package me.vripper.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import me.vripper.ui.LogsController
import me.vripper.ui.PostsController
import me.vripper.ui.SessionStatsController
import me.vripper.ui.SettingsController
import me.vripper.ui.TasksController
import me.vripper.ui.ThreadsController

/**
 * Optional end-to-end check for the controller layer against a live server.
 *
 * Run with `VRIPPER_E2E_PORT=<port> ./gradlew :shared:jvmTest --tests '*ControllersLiveTest*'`.
 */
class ControllersLiveTest {
    @Test
    fun `controllers load and actions do not throw`() = runBlocking {
        val port = System.getenv("VRIPPER_E2E_PORT")?.toIntOrNull() ?: return@runBlocking
        val api = createEndpointApi("127.0.0.1", port, "test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val posts = PostsController(api, scope)
            val threads = ThreadsController(api, scope)
            val tasks = TasksController(api, scope)
            val logs = LogsController(api, scope)
            val settings = SettingsController(api, scope)
            val stats = SessionStatsController(api, scope, "test")

            delay(5_000)

            assertNotNull(posts.posts.value)
            assertNotNull(threads.threads.value)
            assertNotNull(tasks.tasks.value)
            assertNotNull(settings.settings.value)
            println(
                "controllers: posts=${posts.posts.value.size} threads=${threads.threads.value.size} " +
                    "tasks=${tasks.tasks.value.size} speed=${stats.downloadSpeed.value}",
            )
            println("controllers errors: posts=${posts.error} threads=${threads.error} tasks=${tasks.error} settings=${settings.error}")

            val grab = runCatching { threads.grab(1L) }
            println("grab missing thread: ${grab.exceptionOrNull()?.message}")

            posts.startAll()
            posts.stopAll()
            posts.clearCompleted()
            tasks.clear()
            settings.settings.value?.let(settings::save)
            logs.clear()
            delay(4_000)
            println("controllers after actions: postsErr=${posts.error} settingsErr=${settings.error} logged=${logs.logs.value.size}")
        } finally {
            scope.cancel()
            api.close()
        }
    }
}
