package me.vripper.entities

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.vripper.tasks.TaskCategory
import me.vripper.tasks.TaskStatus
import java.time.LocalDateTime

@Serializable
data class TaskEntity(
    val id: Long = -1,
    val name: String,
    val taskCategory: TaskCategory,
    val description: String,
    var status: TaskStatus = TaskStatus.WAITING,
    var result: String = "",
    @Contextual val createdAt: LocalDateTime = LocalDateTime.now(),
    @Contextual var updatedAt: LocalDateTime = LocalDateTime.now(),
)
