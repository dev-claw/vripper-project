package me.vripper.api

import kotlinx.coroutines.flow.Flow
import me.vripper.data.DownloadTask
import me.vripper.data.ForumThread
import me.vripper.data.Image
import me.vripper.data.LogEntry
import me.vripper.data.MovePosition
import me.vripper.data.Post
import me.vripper.data.PostMetadata
import me.vripper.data.QueueState
import me.vripper.data.RemoteImageChunk
import me.vripper.data.Settings
import me.vripper.data.ThreadPostRef
import me.vripper.data.ThreadSelection

/**
 * Remote VRipper endpoint used by the mobile client.
 *
 * All business logic lives on the server; this interface exposes the gRPC
 * operations required to render the mobile screens and trigger user actions.
 */
interface EndpointApi {
    suspend fun getVersion(): String
    suspend fun loggedInUser(): String

    suspend fun findAllPosts(): List<Post>
    suspend fun findPost(postId: Long): Post
    suspend fun getQueueState(): QueueState

    fun onNewPosts(): Flow<Post>
    fun onUpdatePosts(): Flow<Post>
    fun onDeletePosts(): Flow<Long>
    fun onUpdateMetadata(): Flow<PostMetadata>
    fun onQueueStateUpdate(): Flow<QueueState>

    suspend fun scanLinks(links: String)
    suspend fun startAllPosts()
    suspend fun stopAllPosts()
    suspend fun startPosts(ids: List<Long>)
    suspend fun stopPosts(ids: List<Long>)
    suspend fun removePosts(ids: List<Long>)
    suspend fun clearCompletedPosts(): List<Long>
    suspend fun renamePost(id: Long, name: String)
    suspend fun renameToFirst(postIds: List<Long>)
    suspend fun movePost(id: Long, position: MovePosition)

    suspend fun findImages(postId: Long): List<Image>
    fun onUpdateImagesByPost(postId: Long): Flow<Image>
    fun onUpdateImages(): Flow<Image>
    fun downloadImage(imageId: Long): Flow<RemoteImageChunk>

    suspend fun findAllThreads(): List<ForumThread>
    fun onNewThread(): Flow<ForumThread>
    fun onUpdateThread(): Flow<ForumThread>
    fun onDeleteThread(): Flow<Long>
    fun onClearThreads(): Flow<Unit>
    suspend fun removeThreads(ids: List<Long>)
    suspend fun clearThreads()
    suspend fun grabThread(threadId: Long): List<ThreadSelection>
    suspend fun downloadThreadPosts(posts: List<ThreadPostRef>)

    suspend fun findAllTasks(): List<DownloadTask>
    fun onNewTask(): Flow<DownloadTask>
    fun onUpdateTask(): Flow<DownloadTask>
    fun onClearTasks(): Flow<Unit>
    suspend fun clearTasks()

    fun onNewLog(): Flow<LogEntry>
    suspend fun initLogger()

    suspend fun getSettings(): Settings
    suspend fun saveSettings(settings: Settings)
    suspend fun getProxies(): List<String>
    fun onUpdateSettings(): Flow<Settings>

    fun onDownloadSpeed(): Flow<Long>
    fun onVGUserUpdate(): Flow<String>
    fun onErrorCountUpdate(): Flow<Int>
    fun onTasksRunning(): Flow<Boolean>

    fun close()
}

expect fun createEndpointApi(host: String, port: Int, passPhrase: String): EndpointApi
