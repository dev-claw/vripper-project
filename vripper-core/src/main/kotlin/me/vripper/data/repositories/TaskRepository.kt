package me.vripper.data.repositories

import me.vripper.entities.TaskEntity

internal interface TaskRepository {
    fun save(taskEntity: TaskEntity): TaskEntity
    fun update(taskEntity: TaskEntity)
    fun findById(id: Long): TaskEntity?
    fun findAll(): List<TaskEntity>
    fun deleteById(id: Long): Int
    fun deleteAll()
}
