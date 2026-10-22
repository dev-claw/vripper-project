package me.vripper.data.repositories.impl

import me.vripper.data.repositories.TaskRepository
import me.vripper.data.tables.TaskTable
import me.vripper.entities.TaskEntity
import me.vripper.tasks.TaskCategory
import me.vripper.tasks.TaskStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal class TaskRepositoryImpl : TaskRepository {

    override fun save(taskEntity: TaskEntity): TaskEntity {
        val id = TaskTable.insertAndGetId {
            it[name] = taskEntity.name
            it[category] = taskEntity.taskCategory.name
            it[description] = taskEntity.description
            it[status] = taskEntity.status.name
            it[result] = taskEntity.result
            it[createdAt] = taskEntity.createdAt
            it[updatedAt] = taskEntity.updatedAt
        }.value
        return taskEntity.copy(id = id)
    }

    override fun update(taskEntity: TaskEntity) {
        TaskTable.update({ TaskTable.id eq taskEntity.id }) {
            it[status] = taskEntity.status.name
            it[result] = taskEntity.result
            it[updatedAt] = taskEntity.updatedAt
        }
    }

    override fun findById(id: Long): TaskEntity? {
        return TaskTable.selectAll().where {
            TaskTable.id eq id
        }.map(::transform).firstOrNull()
    }

    override fun findAll(): List<TaskEntity> {
        return TaskTable.selectAll()
            .orderBy(TaskTable.createdAt to SortOrder.DESC)
            .map(::transform)
    }

    override fun deleteById(id: Long): Int {
        return TaskTable.deleteWhere { TaskTable.id eq id }
    }

    override fun deleteAll() {
        TaskTable.deleteAll()
    }

    private fun transform(resultRow: ResultRow): TaskEntity {
        return TaskEntity(
            id = resultRow[TaskTable.id].value,
            name = resultRow[TaskTable.name],
            taskCategory = TaskCategory.valueOf(resultRow[TaskTable.category]),
            description = resultRow[TaskTable.description],
            status = TaskStatus.valueOf(resultRow[TaskTable.status]),
            result = resultRow[TaskTable.result],
            createdAt = resultRow[TaskTable.createdAt],
            updatedAt = resultRow[TaskTable.updatedAt],
        )
    }
}
