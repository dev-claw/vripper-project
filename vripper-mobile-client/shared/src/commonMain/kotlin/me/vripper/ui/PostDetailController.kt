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
import me.vripper.data.Image
import me.vripper.data.MovePosition
import me.vripper.data.Post

class PostDetailController(
    private val api: EndpointApi,
    private val postId: Long,
    initialPost: Post?,
    private val scope: CoroutineScope,
) {
    private val _post = MutableStateFlow(initialPost)
    val post: StateFlow<Post?> = _post.asStateFlow()

    private val _images = MutableStateFlow<List<Image>>(emptyList())
    val images: StateFlow<List<Image>> = _images.asStateFlow()

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

    fun start() = action("Unable to start download") { api.startPosts(listOf(postId)) }

    fun stop() = action("Unable to stop download") { api.stopPosts(listOf(postId)) }

    fun rename(name: String) =
        action("Unable to rename post") { api.renamePost(postId, name.trim()) }

    fun renameToFirst() =
        action("Unable to rename post") { api.renameToFirst(listOf(postId)) }

    fun move(position: MovePosition) =
        action("Unable to move post") { api.movePost(postId, position) }

    fun remove(onRemoved: () -> Unit) {
        scope.launch {
            isBusy = true
            try {
                api.removePosts(listOf(postId))
                error = null
                onRemoved()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = t.userMessage("Unable to remove post")
            } finally {
                isBusy = false
            }
        }
    }

    fun dismissError() {
        error = null
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

    private suspend fun load() {
        isLoading = true
        try {
            _post.value = api.findPost(postId)
            _images.value = api.findImages(postId).sortedBy { it.index }
            error = null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            error = t.userMessage("Unable to load post details")
        } finally {
            isLoading = false
        }
    }

    private fun startObservers() {
        scope.observeStream(
            { api.onUpdateImagesByPost(postId) },
            onValue = ::upsertImage,
            onError = ::streamError,
        )
        scope.observeStream(
            api::onUpdateImages,
            onValue = { image -> if (image.postId == postId) upsertImage(image) },
            onError = ::streamError,
        )
        scope.observeStream(
            api::onUpdatePosts,
            onValue = { post -> if (post.id == postId) _post.value = post },
            onError = ::streamError,
        )
    }

    private fun streamError(t: Throwable) {
        error = t.userMessage("Connection to the server was lost")
    }

    private fun upsertImage(image: Image) {
        error = null
        _images.update { current ->
            val index = current.indexOfFirst { it.id == image.id }
            val updated = if (index == -1) {
                current + image
            } else {
                current.toMutableList().also { it[index] = image }
            }
            updated.sortedBy { it.index }
        }
    }
}
