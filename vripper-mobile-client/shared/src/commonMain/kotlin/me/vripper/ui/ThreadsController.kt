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
import me.vripper.data.ForumThread
import me.vripper.data.ThreadPostRef
import me.vripper.data.ThreadSelection

class ThreadsController(
    private val api: EndpointApi,
    private val scope: CoroutineScope,
) {
    private val _threads = MutableStateFlow<List<ForumThread>>(emptyList())
    val threads: StateFlow<List<ForumThread>> = _threads.asStateFlow()

    var isLoading by mutableStateOf(true)
        private set

    var isBusy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set

    init {
        scope.launch {
            load(initial = true)
            startObservers()
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun clearError() {
        error = null
    }

    fun refresh() {
        scope.launch { load(initial = false) }
    }

    fun remove(ids: List<Long>) = action("Unable to remove threads") { api.removeThreads(ids) }

    fun remove(threadId: Long) = remove(listOf(threadId))

    fun clear() = action("Unable to clear threads") { api.clearThreads() }

    suspend fun grab(threadId: Long): List<ThreadSelection> = api.grabThread(threadId)

    suspend fun download(posts: List<ThreadPostRef>) = api.downloadThreadPosts(posts)

    private fun action(errorFallback: String, block: suspend () -> Unit) {
        scope.launch {
            isBusy = true
            try {
                block()
                error = null
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = t.userMessage(errorFallback)
            } finally {
                isBusy = false
            }
        }
    }

    private suspend fun load(initial: Boolean) {
        if (initial) isLoading = true
        try {
            _threads.value = api.findAllThreads()
            error = null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            error = t.userMessage("Unable to load threads")
        } finally {
            isLoading = false
        }
    }

    private fun startObservers() {
        scope.observeStream(api::onNewThread, ::upsert, ::streamError)
        scope.observeStream(api::onUpdateThread, ::upsert, ::streamError)
        scope.observeStream(
            api::onDeleteThread,
            onValue = { id -> _threads.update { current -> current.filterNot { it.id == id } } },
        )
        scope.observeStream(
            api::onClearThreads,
            onValue = { _threads.value = emptyList() },
        )
    }

    private fun streamError(t: Throwable) {
        error = t.userMessage("Connection to the server was lost")
    }

    private fun upsert(thread: ForumThread) {
        error = null
        _threads.update { current ->
            val index = current.indexOfFirst { it.id == thread.id }
            if (index == -1) {
                current + thread
            } else {
                current.toMutableList().also { it[index] = thread }
            }
        }
    }
}
