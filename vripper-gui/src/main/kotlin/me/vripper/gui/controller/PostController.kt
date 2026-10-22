package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.entities.Status
import me.vripper.gui.components.fragments.BytesImageSource
import me.vripper.gui.components.fragments.ImageSource
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.GuiErrorReporter
import me.vripper.model.DownloadRequest
import me.vripper.model.Post
import me.vripper.model.QueueState
import me.vripper.services.IPostCommandEndpoint
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
        GuiErrorReporter.call("posts.scan") {
            currentAppEndpointService().scanLinks(
                postLinks,
                IPostCommandEndpoint.ScanOrigin.PASTED
            )
        }
    }

    suspend fun start(postEntityIdList: List<Long>) {
        GuiErrorReporter.call("posts.start") { currentAppEndpointService().restartAll(postEntityIdList) }
    }

    suspend fun startAll() {
        GuiErrorReporter.call("posts.startAll") { currentAppEndpointService().restartAll() }
    }

    suspend fun delete(postEntityIdList: List<Long>) {
        GuiErrorReporter.call("posts.delete") { currentAppEndpointService().remove(postEntityIdList) }
    }

    suspend fun stop(postEntityIdList: List<Long>) {
        GuiErrorReporter.call("posts.stop") { currentAppEndpointService().stopAll(postEntityIdList) }
    }

    suspend fun clearPosts(): List<Long> {
        return GuiErrorReporter.call("posts.clearCompleted") {
            currentAppEndpointService().clearCompleted()
        }.getOrDefault(emptyList())
    }

    suspend fun stopAll() {
        GuiErrorReporter.call("posts.stopAll") { currentAppEndpointService().stopAll() }
    }

    suspend fun find(postEntityId: Long): PostModel? {
        return GuiErrorReporter.call("posts.find") {
            mapper(currentAppEndpointService().findPost(postEntityId))
        }.getOrNull()
    }

    suspend fun findAllPosts(): List<PostModel> {
        return GuiErrorReporter.call("posts.findAll") {
            currentAppEndpointService().findAllPosts().map(::mapper)
        }.getOrDefault(emptyList())
    }

    suspend fun getQueueState(): QueueState {
        return GuiErrorReporter.call("queue.state") {
            currentAppEndpointService().getQueueState()
        }.getOrDefault(QueueState(0, 0))
    }

    suspend fun rename(postEntityId: Long, value: String) {
        GuiErrorReporter.call("posts.rename") { currentAppEndpointService().rename(postEntityId, value) }
    }

    suspend fun renameToFirst(postEntityIds: List<Long>) {
        GuiErrorReporter.call("posts.renameToFirst") { currentAppEndpointService().renameToFirst(postEntityIds) }
    }

    suspend fun moveTo(postEntityId: Long, position: MovePosition) {
        GuiErrorReporter.call("posts.move") { currentAppEndpointService().move(postEntityId, position) }
    }

    fun toModel(post: Post): PostModel = mapper(post)

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
            -1,
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

    suspend fun getImageSources(item: PostModel): List<ImageSource> {
        val imageEntities = GuiErrorReporter.call("images.findAll") {
            currentAppEndpointService().findImagesByPostEntityId(item.id)
        }.getOrDefault(emptyList())
        return imageEntities.map { imageEntity ->
            BytesImageSource(
                imageEntity.id,
                imageEntity.filename,
                imageEntity.thumbUrl,
                imageEntity.postEntityId,
                imageEntity.status == Status.FINISHED
            ) {
                currentAppEndpointService().downloadImage(DownloadRequest(imageEntity.id))
            }
        }
    }
}