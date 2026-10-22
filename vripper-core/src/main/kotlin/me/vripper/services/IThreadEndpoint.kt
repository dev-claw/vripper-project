package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.model.PostSelection
import me.vripper.model.Thread

interface IThreadEndpoint {
    fun onNewThread(): Flow<Thread>
    fun onUpdateThread(): Flow<Thread>
    fun onDeleteThread(): Flow<Long>
    fun onClearThreads(): Flow<Unit>
    suspend fun findAllThreads(): List<Thread>
    suspend fun threadRemove(threadIdList: List<Long>)
    suspend fun threadClear()
    suspend fun grab(threadId: Long): List<PostSelection>
}
