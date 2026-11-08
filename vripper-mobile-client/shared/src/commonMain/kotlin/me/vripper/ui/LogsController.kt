package me.vripper.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.vripper.api.EndpointApi
import me.vripper.data.LogEntry

class LogsController(
    private val api: EndpointApi,
    private val scope: CoroutineScope,
) {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    var error by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set

    var levelFilter by mutableStateOf<String?>(null)
        private set

    private var maxEntries = DEFAULT_MAX_ENTRIES
    private var nextUid = 0L

    init {
        startObservers()
        scope.launch {
            runCatching { api.getSettings().system.maxEventLog }
                .onSuccess { value -> maxEntries = value.coerceIn(10, 10_000) }
            try {
                api.initLogger()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = t.userMessage("Unable to subscribe to server logs")
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun onLevelFilterChange(level: String?) {
        levelFilter = level
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun retryInit() {
        scope.launch {
            try {
                api.initLogger()
                error = null
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = t.userMessage("Unable to subscribe to server logs")
            }
        }
    }

    private fun startObservers() {
        scope.observeStream(
            api::onNewLog,
            onValue = { entry ->
                error = null
                // The server does not populate a stable log id, so assign a client-side
                // one to keep LazyColumn keys unique.
                val identified = entry.copy(uid = nextUid++)
                _logs.update { current ->
                    val size = (current.size + 1).coerceAtLeast(1)
                    if (size > maxEntries) {
                        current.takeLast(maxEntries - 1) + identified
                    } else {
                        current + identified
                    }
                }
            },
            onError = { t -> error = t.userMessage("Connection to the server was lost") },
        )
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 1_000
    }
}
