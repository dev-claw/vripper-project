package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.model.Post
import me.vripper.model.QueueState
import me.vripper.services.download.MovePosition
import me.vripper.utilities.formatSI
import tornadofx.Controller
import java.time.format.DateTimeFormatter

class PostController : Controller() {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val updatePostsFlow =
        ChannelFlowBuilder.build(
            localAppEndpointService::onUpdatePosts,
            remoteAppEndpointService::onUpdatePosts
        )

    val newPostsFlow = ChannelFlowBuilder.build(
        {
            localAppEndpointService.onNewPosts().map { post ->
                mapper(post)
            }
        }, {
            remoteAppEndpointService.onNewPosts().map { post ->
                mapper(post)
            }
        }
    )

    val deletedPostsFlow =
        ChannelFlowBuilder.build(
            localAppEndpointService::onDeletePosts,
            remoteAppEndpointService::onDeletePosts
        )

    val updateMetadataFlow = ChannelFlowBuilder.build(
        localAppEndpointService::onUpdateMetadata,
        remoteAppEndpointService::onUpdateMetadata,
    )

    val queueStateUpdate = ChannelFlowBuilder.build(
        localAppEndpointService::onQueueStateUpdate,
        remoteAppEndpointService::onQueueStateUpdate,
    )

    suspend fun scan(postLinks: String) {
        runCatching { currentAppEndpointService().scanLinks(postLinks) }
    }

    suspend fun start(postEntityIdList: List<Long>) {
        runCatching { currentAppEndpointService().restartAll(postEntityIdList) }
    }

    suspend fun startAll() {
        runCatching { currentAppEndpointService().restartAll() }
    }

    suspend fun delete(postEntityIdList: List<Long>) {
        runCatching { currentAppEndpointService().remove(postEntityIdList) }
    }

    suspend fun stop(postEntityIdList: List<Long>) {
        runCatching { currentAppEndpointService().stopAll(postEntityIdList) }
    }

    suspend fun clearPosts(): List<Long> {
        return runCatching { return currentAppEndpointService().clearCompleted() }.getOrDefault(emptyList())
    }

    suspend fun stopAll() {
        runCatching { currentAppEndpointService().stopAll() }
    }

    suspend fun find(postEntityId: Long): PostModel? {
        return runCatching { mapper(currentAppEndpointService().findPost(postEntityId)) }.getOrNull()
    }

    suspend fun findAllPosts(): List<PostModel> {
        return currentAppEndpointService().findAllPosts().map(::mapper)
    }

    suspend fun getQueueState(): QueueState {
        return runCatching { currentAppEndpointService().getQueueState() }.getOrDefault(QueueState(0, 0))
    }

    suspend fun rename(postEntityId: Long, value: String) {
        runCatching { currentAppEndpointService().rename(postEntityId, value) }
    }

    suspend fun renameToFirst(postEntityIds: List<Long>) {
        runCatching { currentAppEndpointService().renameToFirst(postEntityIds) }
    }

    suspend fun moveTo(postEntityId: Long, position: MovePosition) {
        runCatching { currentAppEndpointService().move(postEntityId, position) }
    }

    private fun mapper(post: Post): PostModel {
        return PostModel(
            post.id,
            post.postId,
            post.postTitle,
            progress(post.total, post.done),
            post.status.name,
            post.url,
            post.done,
            post.total,
            post.hosts.joinToString(separator = ", "),
            post.addedOn.format(dateTimeFormatter),
            "*",
            post.getDownloadFolder(),
            post.folderName,
            progressCount(post.total, post.done, post.downloaded),
            post.previews,
            post.resolvedNames,
            post.postedBy,
            post.threadId
        )
    }

    fun progressCount(total: Int, done: Int, downloaded: Long): String {
        return "${done}/${total} (${downloaded.formatSI()})"
    }

    fun progress(total: Int, done: Int): Double {
        return if (done == 0 && total == 0) 0.0 else (done.toDouble() / total)
    }
}