package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.model.*

interface IAppEndpointService {
    suspend fun scanLinks(postLinks: String)
    suspend fun restartAll(posIds: List<Long> = listOf())
    suspend fun remove(postIdList: List<Long>)
    suspend fun stopAll(postIdList: List<Long> = emptyList())
    suspend fun clearCompleted(): List<Long>
    suspend fun findPost(postId: Long): Post
    suspend fun findAllPosts(): List<Post>
    suspend fun rename(postId: Long, newName: String)
    fun onNewPosts(): Flow<Post>
    fun onUpdatePosts(): Flow<Post>
    fun onDeletePosts(): Flow<Long>
    fun onUpdateMetadata(): Flow<Metadata>
    suspend fun findImagesByPostId(postId: Long): List<Image>
    fun onUpdateImagesByPostId(postId: Long): Flow<Image>
    fun onUpdateImages(): Flow<Image>
    fun onStopped(): Flow<Long>
    fun onNewLog(): Flow<LogEntry>
    fun onNewThread(): Flow<Thread>
    fun onUpdateThread(): Flow<Thread>
    fun onDeleteThread(): Flow<Long>
    fun onClearThreads(): Flow<Unit>
    suspend fun findAllThreads(): List<Thread>
    suspend fun threadRemove(threadIdList: List<Long>)
    suspend fun threadClear()
    suspend fun grab(threadId: Long): List<PostSelection>
    suspend fun download(posts: List<ThreadPostId>)
    fun onDownloadSpeed(): Flow<DownloadSpeed>
    fun onVGUserUpdate(): Flow<String>
    fun onQueueStateUpdate(): Flow<QueueState>
    fun onErrorCountUpdate(): Flow<ErrorCount>
    fun onTasksRunning(): Flow<Boolean>
    suspend fun getSettings(): Settings
    suspend fun saveSettings(settings: Settings)
    suspend fun getProxies(): List<String>
    fun onUpdateSettings(): Flow<Settings>
    suspend fun loggedInUser(): String
    suspend fun getVersion(): String
    suspend fun renameToFirst(postIds: List<Long>)
    suspend fun dbMigration(): String
    suspend fun initLogger()
}