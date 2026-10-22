package me.vripper.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.data.repositories.TaskRepository
import me.vripper.entities.TaskEntity
import me.vripper.event.*
import me.vripper.tasks.ITask
import me.vripper.tasks.TaskStatus
import me.vripper.utilities.LoggerDelegate
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

internal class TaskService(
    private val taskRepository: TaskRepository,
    private val eventBus: EventBus,
) {
    private val taskRunner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val log by LoggerDelegate()
    private var current = 0

    fun submit(task: ITask) {
        val taskEntity = runCatching { register(task) }
            .onFailure { log.error("Unable to persist task ${task.getName()}", it) }
            .getOrNull()

        taskRunner.launch {
            if (taskEntity != null) {
                runCatching { markRunning(taskEntity.id) }
                    .onFailure { log.error("Unable to mark task ${taskEntity.id} as running", it) }
            }
            increment()
            try {
                task.run()
                if (taskEntity != null) {
                    runCatching { complete(taskEntity.id, task.getStatus(), task.getResult()) }
                        .onFailure { log.error("Unable to complete task ${taskEntity.id}", it) }
                }
            } catch (e: Exception) {
                log.error("Task ${task.getName()} failed", e)
                if (taskEntity != null) {
                    runCatching {
                        complete(taskEntity.id, TaskStatus.FAILURE, e.message ?: e.toString())
                    }.onFailure { log.error("Unable to mark task ${taskEntity.id} as failed", it) }
                }
            } finally {
                decrement()
            }
        }
    }

    @Synchronized
    fun increment() {
        if (current == 0) {
            eventBus.publishEvent(LoadingTasks(true))
        }
        current += 1
    }

    @Synchronized
    fun decrement() {
        current -= 1
        if (current == 0) {
            eventBus.publishEvent(LoadingTasks(false))
        }
    }

    fun register(task: ITask): TaskEntity {
        val taskEntity = TaskEntity(
            name = task.getName(),
            taskCategory = task.getCategory(),
            description = task.getDescription(),
        )
        val saved = transaction { taskRepository.save(taskEntity) }
        log.debug("[{}] Publishing event: TaskCreateEvent for task {}", System.currentTimeMillis(), saved.id)
        eventBus.publishEvent(TaskCreateEvent(saved))
        return saved
    }

    fun markRunning(taskId: Long) {
        update(taskId, TaskStatus.RUNNING, "")
    }

    fun complete(taskId: Long, status: TaskStatus, result: String) {
        update(taskId, status, result)
    }

    private fun update(taskId: Long, status: TaskStatus, result: String) {
        val current = transaction { taskRepository.findById(taskId) } ?: return
        val updated = current.copy(
            status = status,
            result = result,
            updatedAt = LocalDateTime.now(),
        )
        transaction { taskRepository.update(updated) }
        log.debug("[{}] Publishing event: TaskUpdateEvent for task {}", System.currentTimeMillis(), taskId)
        eventBus.publishEvent(TaskUpdateEvent(updated))
    }

    fun findAll(): List<TaskEntity> {
        return transaction { taskRepository.findAll() }
    }

    suspend fun findAllAsync(): List<TaskEntity> {
        return dbQuery { taskRepository.findAll() }
    }

    fun clear() {
        transaction { taskRepository.deleteAll() }
        log.debug("[{}] Publishing event: TaskClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(TaskClearEvent())
    }

    suspend fun clearAsync() {
        dbQuery { taskRepository.deleteAll() }
        log.debug("[{}] Publishing event: TaskClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(TaskClearEvent())
    }

    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
