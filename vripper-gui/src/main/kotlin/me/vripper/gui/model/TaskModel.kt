package me.vripper.gui.model

import me.vripper.tasks.TaskCategory
import me.vripper.tasks.TaskStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TaskModel(
    val id: Long,
    val name: String,
    val taskCategory: TaskCategory,
    val description: String,
    val status: TaskStatus,
    val result: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    fun updatedAtFormatted(): String = updatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
