package me.vripper.gui.controller

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.map
import me.vripper.entities.ThreadEntity
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.model.ThreadSelectionModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder.toFlow
import me.vripper.model.ThreadPostId
import org.koin.core.component.KoinComponent
import tornadofx.Controller

class ThreadController : KoinComponent, Controller() {

    val newThread = currentAppEndpointService().onNewThread().map(::threadModelMapper).cancellable()

    val updateThread = currentAppEndpointService().onUpdateThread().cancellable()

    val deleteThread = currentAppEndpointService().onDeleteThread().cancellable()

    val clearThreads = currentAppEndpointService().onClearThreads().cancellable()

    fun findAll(): Flow<ThreadModel> {
        return toFlow { currentAppEndpointService().findAllThreads().map(::threadModelMapper) }
    }

    private fun threadModelMapper(it: ThreadEntity): ThreadModel {
        return ThreadModel(
            it.title,
            it.link,
            it.total,
            it.threadId
        )
    }

    suspend fun delete(threadIdList: List<Long>) {
        currentAppEndpointService().threadRemove(threadIdList)
    }

    suspend fun clearAll() {
        currentAppEndpointService().threadClear()
    }

    suspend fun grab(threadId: Long): List<ThreadSelectionModel> =
        currentAppEndpointService().grab(threadId).map { postItem ->
            ThreadSelectionModel(
                postItem.number,
                postItem.title,
                postItem.url,
                postItem.hosts,
                postItem.postId,
                postItem.threadId,
                postItem.previews.take(4)
            )
        }

    suspend fun download(selectedItems: List<ThreadSelectionModel>) {
        currentAppEndpointService().download(selectedItems.map {
            ThreadPostId(
                it.threadId,
                it.postId
            )
        })
    }
}