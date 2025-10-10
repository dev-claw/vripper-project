package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.model.*
import me.vripper.services.download.MovePosition

interface IAppEndpointService {
    suspend fun scanLinks(postLinks: String)
    suspend fun restartAll(postEntityIds: List<Long> = listOf())
    suspend fun remove(postEntityIds: List<Long>)
    suspend fun stopAll(postEntityIds: List<Long> = emptyList())
    suspend fun clearCompleted(): List<Long>
    suspend fun findPost(postEntityId: Long): Post
    suspend fun findAllPosts(): List<Post>
    suspend fun rename(postEntityId: Long, newName: String)
    fun onNewPosts(): Flow<Post>
    fun onUpdatePosts(): Flow<Post>
    fun onDeletePosts(): Flow<Long>
    fun onUpdateMetadata(): Flow<Metadata>
    suspend fun findImagesByPostEntityId(postEntityId: Long): List<Image>
    fun onUpdateImagesByPostEntityId(postEntityId: Long): Flow<Image>
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
    suspend fun getQueueState(): QueueState
    fun onQueueStateUpdate(): Flow<QueueState>
    fun onErrorCountUpdate(): Flow<ErrorCount>
    fun onTasksRunning(): Flow<Boolean>
    suspend fun getSettings(): Settings
    suspend fun saveSettings(settings: Settings)
    suspend fun getProxies(): List<String>
    fun onUpdateSettings(): Flow<Settings>
    suspend fun loggedInUser(): String
    suspend fun getVersion(): String
    suspend fun renameToFirst(postEntityIds: List<Long>)
    suspend fun dbMigration(): String
    suspend fun initLogger()
    suspend fun move(postEntityId: Long, position: MovePosition)
    fun connectionState(): String
}