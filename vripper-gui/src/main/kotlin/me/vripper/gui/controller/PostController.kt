package me.vripper.gui.controller

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.ChannelFlowBuilder.toFlow
import me.vripper.model.Post
import me.vripper.utilities.formatSI
import tornadofx.Controller
import java.time.format.DateTimeFormatter

class PostController : Controller() {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")

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

    suspend fun scan(postLinks: String) {
        runCatching { currentAppEndpointService().scanLinks(postLinks) }
    }

    suspend fun start(postIdList: List<Long>) {
        runCatching { currentAppEndpointService().restartAll(postIdList) }
    }

    suspend fun startAll() {
        runCatching { currentAppEndpointService().restartAll() }
    }

    suspend fun delete(postIdList: List<Long>) {
        runCatching { currentAppEndpointService().remove(postIdList) }
    }

    suspend fun stop(postIdList: List<Long>) {
        runCatching { currentAppEndpointService().stopAll(postIdList) }
    }

    suspend fun clearPosts(): List<Long> {
        return runCatching { return currentAppEndpointService().clearCompleted() }.getOrDefault(emptyList())
    }

    suspend fun stopAll() {
        runCatching { currentAppEndpointService().stopAll() }
    }

    suspend fun find(postId: Long): PostModel? {
        return runCatching { mapper(currentAppEndpointService().findPost(postId)) }.getOrNull()
    }

    fun findAllPosts(): Flow<PostModel> {
        return toFlow { currentAppEndpointService().findAllPosts().map(::mapper) }
    }

    suspend fun rename(postId: Long, value: String) {
        runCatching { currentAppEndpointService().rename(postId, value) }
    }

    suspend fun renameToFirst(postIds: List<Long>) {
        runCatching { currentAppEndpointService().renameToFirst(postIds) }
    }

    private fun mapper(post: Post): PostModel {
        return PostModel(
            post.postId,
            post.postTitle,
            progress(post.total, post.done),
            post.status.name,
            post.url,
            post.done,
            post.total,
            post.hosts.joinToString(separator = ", "),
            post.addedOn.format(dateTimeFormatter),
            post.rank + 1,
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