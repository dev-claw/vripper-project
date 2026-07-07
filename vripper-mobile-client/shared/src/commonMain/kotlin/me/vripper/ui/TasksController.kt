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
import me.vripper.data.DownloadTask

class TasksController(
    private val api: EndpointApi,
    private val scope: CoroutineScope,
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    var isLoading by mutableStateOf(true)
        private set

    var isBusy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            load()
            startObservers()
        }
    }

    fun refresh() {
        scope.launch { load() }
    }

    fun clear() {
        scope.launch {
            isBusy = true
            try {
                api.clearTasks()
                error = null
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = t.userMessage("Unable to clear tasks")
            } finally {
                isBusy = false
            }
        }
    }

    private suspend fun load() {
        isLoading = true
        try {
            _tasks.value = api.findAllTasks().sortedByDescending { it.updatedAt }
            error = null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            error = t.userMessage("Unable to load tasks")
        } finally {
            isLoading = false
        }
    }

    private fun startObservers() {
        scope.observeStream(api::onNewTask, ::upsert, ::streamError)
        scope.observeStream(api::onUpdateTask, ::upsert, ::streamError)
        scope.observeStream(
            api::onClearTasks,
            onValue = { _tasks.value = emptyList() },
        )
    }

    private fun streamError(t: Throwable) {
        error = t.userMessage("Connection to the server was lost")
    }

    private fun upsert(task: DownloadTask) {
        error = null
        _tasks.update { current ->
            val index = current.indexOfFirst { it.id == task.id }
            val updated = if (index == -1) {
                current + task
            } else {
                current.toMutableList().also { it[index] = task }
            }
            updated.sortedByDescending { it.updatedAt }
        }
    }
}
