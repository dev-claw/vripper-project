package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.model.*
import me.vripper.services.download.MovePosition

interface IDownloadEndpoint {
    suspend fun download(posts: List<ThreadPostId>)
    fun onDownloadSpeed(): Flow<DownloadSpeed>
    suspend fun getQueueState(): QueueState
    fun onQueueStateUpdate(): Flow<QueueState>
    fun onErrorCountUpdate(): Flow<ErrorCount>
    fun onTasksRunning(): Flow<Boolean>
    suspend fun move(postEntityId: Long, position: MovePosition)
    fun downloadImage(downloadRequest: DownloadRequest): Flow<ImageChunk>
}
