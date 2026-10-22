package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.entities.TaskEntity

interface ITaskEndpoint {
    suspend fun findAllTasks(): List<TaskEntity>
    fun onNewTask(): Flow<TaskEntity>
    fun onUpdateTask(): Flow<TaskEntity>
    suspend fun clearTasks()
    fun onClearTasks(): Flow<Unit>
}
