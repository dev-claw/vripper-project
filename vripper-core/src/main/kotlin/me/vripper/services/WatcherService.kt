package me.vripper.services

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.vripper.model.ViperSettings
import me.vripper.tasks.SubscriptionsTask
import me.vripper.tasks.TaskStatus
import me.vripper.tasks.ThreadRefreshTask
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import me.vripper.utilities.LoggerDelegate
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

internal class WatcherService(
    private val settingsService: SettingsService,
    private val taskService: TaskService,
    private val vgAuthService: VGAuthService,
) {
    private val log by LoggerDelegate()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val statePath = VRIPPER_DIR.resolve("watcher-state.json")
    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val refreshInFlight = AtomicBoolean(false)
    private var job: Job? = null

    @Volatile
    private var currentState: WatcherState = loadState()

    fun start() {
        if (job?.isActive == true) {
            return
        }
        job = coroutineScope.launch { loop() }
    }

    fun stop() {
        val current = job ?: return
        current.cancel()
        runBlocking { withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { current.join() } }
        job = null
    }

    fun state(): WatcherState = currentState

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val settings = settingsService.settings.viperSettings
            if (!isEnabled(settings)) {
                delay(IDLE_POLL_MILLIS.milliseconds)
                continue
            }

            val interval = settings.watchIntervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES) * 60_000L
            val nextDelay = watcherNextDelayMs(
                lastCheckedEpochMs = currentState.lastCheckedEpochMs,
                intervalMs = interval,
                nowMs = System.currentTimeMillis(),
            )
            val backoff = watcherBackoffMs(currentState.failureCount, interval)
            val wait = maxOf(nextDelay, backoff)
            if (wait > 0) {
                val capped = wait.coerceAtMost(IDLE_POLL_MILLIS)
                val jitter = if (wait <= capped) Random.nextLong(0, JITTER_MILLIS) else 0
                delay((capped + jitter).milliseconds)
                if (capped < wait) {
                    continue
                }
            }

            if (!isEnabled(settingsService.settings.viperSettings)) {
                continue
            }
            runRefresh()
        }
    }

    private suspend fun runRefresh() {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return
        }
        val taskJobs = mutableListOf<Job>()
        try {
            val tasks = buildList {
                if (vgAuthService.isAuthenticated()) {
                    add(SubscriptionsTask())
                }
                add(ThreadRefreshTask())
            }
            val results = tasks.map { task ->
                val job = taskService.submit(task)
                taskJobs.add(job)
                job.join()
                task to task.getStatus()
            }
            val status = when {
                results.any { it.second == TaskStatus.FAILURE } -> TaskStatus.FAILURE
                results.any { it.second == TaskStatus.WARNING } -> TaskStatus.WARNING
                else -> TaskStatus.SUCCESS
            }
            val error = results
                .filter { it.second == TaskStatus.FAILURE || it.second == TaskStatus.WARNING }
                .joinToString("; ") { it.first.getResult() }
                .take(MAX_ERROR_LENGTH)
            currentState = currentState.copy(
                lastCheckedEpochMs = System.currentTimeMillis(),
                lastStatus = status.name,
                lastError = error,
                failureCount = if (status == TaskStatus.FAILURE) currentState.failureCount + 1 else 0,
            )
            saveState()
            log.info("Watcher finished with status {}", status)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                taskJobs.forEach { it.cancelAndJoin() }
            }
            throw e
        } catch (e: Exception) {
            currentState = currentState.copy(
                lastCheckedEpochMs = System.currentTimeMillis(),
                lastStatus = TaskStatus.FAILURE.name,
                lastError = (e.message ?: e.toString()).take(MAX_ERROR_LENGTH),
                failureCount = currentState.failureCount + 1,
            )
            saveState()
            log.error("Watcher failed", e)
        } finally {
            refreshInFlight.set(false)
        }
    }

    private fun isEnabled(settings: ViperSettings): Boolean {
        return settings.watchSubscriptions
    }

    private fun loadState(): WatcherState {
        return try {
            if (statePath.exists()) {
                json.decodeFromString(statePath.readText())
            } else {
                WatcherState()
            }
        } catch (e: Exception) {
            log.warn("Unable to read watcher state", e)
            WatcherState()
        }
    }

    private fun saveState() {
        val tmp = statePath.resolveSibling("${statePath.fileName}.tmp")
        try {
            Files.writeString(
                tmp,
                json.encodeToString(currentState),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC,
            )
            try {
                Files.move(
                    tmp,
                    statePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, statePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            log.error("Unable to save watcher state", e)
        }
    }

    companion object {
        private const val MIN_INTERVAL_MINUTES = 5
        private const val MAX_INTERVAL_MINUTES = 1440
        private const val IDLE_POLL_MILLIS = 60_000L
        private const val JITTER_MILLIS = 30_000L
        private const val MAX_BACKOFF_MILLIS = 6 * 60 * 60 * 1000L
        private const val MAX_ERROR_LENGTH = 1_000
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        internal fun watcherNextDelayMs(lastCheckedEpochMs: Long, intervalMs: Long, nowMs: Long): Long {
            if (lastCheckedEpochMs <= 0) {
                return 0
            }
            return (lastCheckedEpochMs + intervalMs - nowMs).coerceAtLeast(0)
        }

        internal fun watcherBackoffMs(failureCount: Int, intervalMs: Long): Long {
            if (failureCount <= 0) {
                return 0
            }
            val factor = 1L shl failureCount.coerceAtMost(6)
            return (intervalMs * factor).coerceAtMost(MAX_BACKOFF_MILLIS)
        }
    }
}

@Serializable
internal data class WatcherState(
    val lastCheckedEpochMs: Long = 0,
    val lastStatus: String = "",
    val lastError: String = "",
    val failureCount: Int = 0,
)
