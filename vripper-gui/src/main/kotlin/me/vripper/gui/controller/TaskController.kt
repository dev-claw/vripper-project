package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.entities.TaskEntity
import me.vripper.gui.model.TaskModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.GuiErrorReporter
import tornadofx.Controller

class TaskController : Controller() {

    val newTask = ChannelFlowBuilder.build(
        { localAppEndpointService.onNewTask().map(::mapper) },
        { remoteAppEndpointService.onNewTask().map(::mapper) },
    )

    val updateTask = ChannelFlowBuilder.build(
        { localAppEndpointService.onUpdateTask().map(::mapper) },
        { remoteAppEndpointService.onUpdateTask().map(::mapper) },
    )

    val clearTasks = ChannelFlowBuilder.build(
        localAppEndpointService::onClearTasks,
        remoteAppEndpointService::onClearTasks,
    )

    suspend fun findAll(): List<TaskModel> {
        return GuiErrorReporter.call("tasks.findAll") {
            currentAppEndpointService().findAllTasks().map(::mapper)
        }.getOrDefault(emptyList())
    }

    suspend fun clearAll() {
        GuiErrorReporter.call("tasks.clear") { currentAppEndpointService().clearTasks() }
    }

    private fun mapper(task: TaskEntity): TaskModel {
        return TaskModel(
            id = task.id,
            name = task.name,
            taskCategory = task.taskCategory,
            description = task.description,
            status = task.status,
            result = task.result,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
