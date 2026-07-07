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
import me.vripper.data.MovePosition
import me.vripper.data.Post
import me.vripper.data.PostMetadata
import me.vripper.data.QueueState

class PostsController(
    private val api: EndpointApi,
    private val scope: CoroutineScope,
) {
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    var isLoading by mutableStateOf(true)
        private set

    var isRefreshing by mutableStateOf(false)
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

    fun startAll() = action("Unable to start downloads") { api.startAllPosts() }

    fun stopAll() = action("Unable to stop downloads") { api.stopAllPosts() }

    fun start(postId: Long) = action("Unable to start download") { api.startPosts(listOf(postId)) }

    fun stop(postId: Long) = action("Unable to stop download") { api.stopPosts(listOf(postId)) }

    fun remove(postId: Long) = action("Unable to remove post") { api.removePosts(listOf(postId)) }

    fun rename(postId: Long, name: String) =
        action("Unable to rename post") { api.renamePost(postId, name.trim()) }

    fun renameToFirst(postId: Long) =
        action("Unable to rename post") { api.renameToFirst(listOf(postId)) }

    fun move(postId: Long, position: MovePosition) =
        action("Unable to move post") { api.movePost(postId, position) }

    fun scanLinks(links: String) {
        val input = links.trim()
        if (input.isEmpty()) return
        action("Unable to scan links") { api.scanLinks(input) }
    }

    fun clearCompleted() = action("Unable to clear finished posts") {
        val removed = api.clearCompletedPosts()
        if (removed.isNotEmpty()) {
            _posts.update { current -> current.filterNot { it.id in removed } }
        }
    }

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
        if (initial) isLoading = true else isRefreshing = true
        try {
            _posts.value = api.findAllPosts()
            _queueState.value = api.getQueueState()
            error = null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            error = t.userMessage("Unable to load posts")
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    private fun startObservers() {
        scope.observeStream(api::onNewPosts, ::upsert, ::streamError)
        scope.observeStream(api::onUpdatePosts, ::upsert, ::streamError)
        scope.observeStream(
            api::onDeletePosts,
            onValue = { id -> _posts.update { current -> current.filterNot { it.id == id } } },
        )
        scope.observeStream(api::onUpdateMetadata, ::applyMetadata, ::streamError)
        scope.observeStream(
            api::onQueueStateUpdate,
            onValue = { state -> _queueState.value = state },
        )
    }

    private fun streamError(t: Throwable) {
        error = t.userMessage("Connection to the server was lost")
    }

    private fun upsert(post: Post) {
        error = null
        _posts.update { current ->
            val index = current.indexOfFirst { it.id == post.id }
            if (index == -1) {
                current + post
            } else {
                current.toMutableList().also { it[index] = post }
            }
        }
    }

    private fun applyMetadata(metadata: PostMetadata) {
        error = null
        _posts.update { current ->
            current.map { post ->
                if (post.id != metadata.postId) {
                    post
                } else {
                    post.copy(
                        postedBy = metadata.postedBy,
                        resolvedNames = metadata.resolvedNames,
                    )
                }
            }
        }
    }
}
