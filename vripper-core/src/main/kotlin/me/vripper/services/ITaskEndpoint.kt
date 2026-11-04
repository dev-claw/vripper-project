package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.entities.TaskEntity

interface ITaskEndpoint {
    suspend fun findAllTasks(): List<TaskEntity>
    suspend fun findTasksByPostEntityId(postEntityId: Long): List<TaskEntity>
    fun onNewTask(): Flow<TaskEntity>
    fun onUpdateTask(): Flow<TaskEntity>
    suspend fun dismissTask(taskId: Long)
    fun onDismissTask(): Flow<Long>
    suspend fun clearTasks()
    fun onClearTasks(): Flow<Unit>
}
