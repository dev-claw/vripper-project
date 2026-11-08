package me.vripper.ui

import kotlinx.coroutines.flow.Flow
import me.vripper.api.EndpointApi
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

/** Minimal [EndpointApi] for controller tests; unused members fail loudly. */
open class FakeEndpointApi : EndpointApi {
    override suspend fun getVersion(): String = throw NotImplementedError()
    override suspend fun loggedInUser(): String = throw NotImplementedError()
    override suspend fun findAllPosts(): List<Post> = throw NotImplementedError()
    override suspend fun findPost(postId: Long): Post = throw NotImplementedError()
    override suspend fun getQueueState(): QueueState = throw NotImplementedError()
    override fun onNewPosts(): Flow<Post> = throw NotImplementedError()
    override fun onUpdatePosts(): Flow<Post> = throw NotImplementedError()
    override fun onDeletePosts(): Flow<Long> = throw NotImplementedError()
    override fun onUpdateMetadata(): Flow<PostMetadata> = throw NotImplementedError()
    override fun onQueueStateUpdate(): Flow<QueueState> = throw NotImplementedError()
    override suspend fun scanLinks(links: String): Unit = throw NotImplementedError()
    override suspend fun startAllPosts(): Unit = throw NotImplementedError()
    override suspend fun stopAllPosts(): Unit = throw NotImplementedError()
    override suspend fun startPosts(ids: List<Long>): Unit = throw NotImplementedError()
    override suspend fun stopPosts(ids: List<Long>): Unit = throw NotImplementedError()
    override suspend fun removePosts(ids: List<Long>): Unit = throw NotImplementedError()
    override suspend fun clearCompletedPosts(): List<Long> = throw NotImplementedError()
    override suspend fun renamePost(id: Long, name: String): Unit = throw NotImplementedError()
    override suspend fun renameToFirst(postIds: List<Long>): Unit = throw NotImplementedError()
    override suspend fun movePost(id: Long, position: MovePosition): Unit = throw NotImplementedError()
    override suspend fun findImages(postId: Long): List<Image> = throw NotImplementedError()
    override fun onUpdateImagesByPost(postId: Long): Flow<Image> = throw NotImplementedError()
    override fun onUpdateImages(): Flow<Image> = throw NotImplementedError()
    override fun downloadImage(imageId: Long): Flow<RemoteImageChunk> = throw NotImplementedError()
    override suspend fun findAllThreads(): List<ForumThread> = throw NotImplementedError()
    override fun onNewThread(): Flow<ForumThread> = throw NotImplementedError()
    override fun onUpdateThread(): Flow<ForumThread> = throw NotImplementedError()
    override fun onDeleteThread(): Flow<Long> = throw NotImplementedError()
    override fun onClearThreads(): Flow<Unit> = throw NotImplementedError()
    override suspend fun removeThreads(ids: List<Long>): Unit = throw NotImplementedError()
    override suspend fun clearThreads(): Unit = throw NotImplementedError()
    override suspend fun grabThread(threadId: Long): List<ThreadSelection> = throw NotImplementedError()
    override suspend fun downloadThreadPosts(posts: List<ThreadPostRef>): Unit = throw NotImplementedError()
    override suspend fun findAllTasks(): List<DownloadTask> = throw NotImplementedError()
    override fun onNewTask(): Flow<DownloadTask> = throw NotImplementedError()
    override fun onUpdateTask(): Flow<DownloadTask> = throw NotImplementedError()
    override fun onClearTasks(): Flow<Unit> = throw NotImplementedError()
    override suspend fun clearTasks(): Unit = throw NotImplementedError()
    override fun onNewLog(): Flow<LogEntry> = throw NotImplementedError()
    override suspend fun initLogger(): Unit = throw NotImplementedError()
    override suspend fun getSettings(): Settings = throw NotImplementedError()
    override suspend fun saveSettings(settings: Settings): Unit = throw NotImplementedError()
    override suspend fun getProxies(): List<String> = throw NotImplementedError()
    override fun onUpdateSettings(): Flow<Settings> = throw NotImplementedError()
    override fun onDownloadSpeed(): Flow<Long> = throw NotImplementedError()
    override fun onVGUserUpdate(): Flow<String> = throw NotImplementedError()
    override fun onErrorCountUpdate(): Flow<Int> = throw NotImplementedError()
    override fun onTasksRunning(): Flow<Boolean> = throw NotImplementedError()
    override fun close() = Unit
}
