package me.vripper.gui.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.vripper.entities.TaskEntity
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.model.TaskModel
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.GuiErrorReporter
import me.vripper.tasks.TaskStatus
import tornadofx.Controller

class TaskController : Controller() {

    private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskMap = MutableStateFlow<Map<Long, TaskModel>>(emptyMap())

    val tasks: StateFlow<List<TaskModel>> = taskMap
        .map { current ->
            current.values.sortedWith(
                compareByDescending<TaskModel> { it.updatedAt }.thenByDescending { it.id }
            )
        }
        .stateIn(taskScope, SharingStarted.Eagerly, emptyList())

    val runningTask: StateFlow<TaskModel?> = taskMap
        .map { current ->
            current.values
                .filter { it.status == TaskStatus.RUNNING }
                .maxWithOrNull(compareBy<TaskModel> { it.updatedAt }.thenBy { it.id })
        }
        .stateIn(taskScope, SharingStarted.Eagerly, null)

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

    init {
        taskScope.launch {
            newTask.collect { upsert(it) }
        }
        taskScope.launch {
            updateTask.collect { upsert(it) }
        }
        taskScope.launch {
            clearTasks.collect { taskMap.value = emptyMap() }
        }
        taskScope.launch {
            GuiEventBus.events.collect { event ->
                when (event) {
                    GuiEventBus.LocalSession, GuiEventBus.RemoteSession -> refresh()
                    GuiEventBus.ChangingSession, is GuiEventBus.RemoteError -> taskMap.value = emptyMap()
                    else -> {}
                }
            }
        }
        if (AppEndpointManager.currentAppState() == GuiEventBus.LocalSession ||
            AppEndpointManager.currentAppState() == GuiEventBus.RemoteSession
        ) {
            taskScope.launch { refresh() }
        }
    }

    suspend fun findAll(): List<TaskModel> {
        return GuiErrorReporter.call("tasks.findAll") {
            currentAppEndpointService().findAllTasks().map(::mapper)
        }.getOrDefault(emptyList())
    }

    suspend fun clearAll() {
        GuiErrorReporter.call("tasks.clear") { currentAppEndpointService().clearTasks() }
    }

    private suspend fun refresh() {
        taskMap.value = findAll().associateBy { it.id }
    }

    private fun upsert(task: TaskModel) {
        taskMap.update { current -> current + (task.id to task) }
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
