package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.gui.model.PostModel
import me.vripper.model.Post
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.formatSI
import tornadofx.Controller
import java.time.format.DateTimeFormatter

class PostController : Controller() {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")

    lateinit var appEndpointService: IAppEndpointService

    suspend fun scan(postLinks: String) {
        appEndpointService.scanLinks(postLinks)
    }

    suspend fun start(postIdList: List<Long>) {
        appEndpointService.restartAll(postIdList)
    }

    suspend fun startAll() {
        appEndpointService.restartAll()
    }

    suspend fun delete(postIdList: List<Long>) {
        appEndpointService.remove(postIdList)
    }

    suspend fun stop(postIdList: List<Long>) {
        appEndpointService.stopAll(postIdList)
    }

    suspend fun clearPosts(): List<Long> {
        return appEndpointService.clearCompleted()
    }

    suspend fun stopAll() {
        appEndpointService.stopAll()
    }

    suspend fun find(postId: Long): PostModel {
        return mapper(appEndpointService.findPost(postId))
    }

    suspend fun findAllPosts(): List<PostModel> {
        return appEndpointService.findAllPosts().map(::mapper)
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

    suspend fun rename(postId: Long, value: String) {
        appEndpointService.rename(postId, value)
    }

    suspend fun renameToFirst(postIds: List<Long>) {
        appEndpointService.renameToFirst(postIds)
    }

    fun onNewPosts() =
        appEndpointService.onNewPosts().map { post ->
            mapper(post)
        }

    fun onUpdatePosts() =
        appEndpointService.onUpdatePosts()

    fun onDeletePosts() =
        appEndpointService.onDeletePosts()

    fun onUpdateMetadata() =
        appEndpointService.onUpdateMetadata()
}