package me.vripper.gui.controller

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.vripper.entities.ThreadEntity
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.model.ThreadSelectionModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.ChannelFlowBuilder.toFlow
import me.vripper.model.ThreadPostId
import org.koin.core.component.KoinComponent
import tornadofx.Controller

class ThreadController : KoinComponent, Controller() {

    val newThread = ChannelFlowBuilder.build(
        {
            localAppEndpointService.onNewThread().map(::threadModelMapper)
        },
        {
            remoteAppEndpointService.onNewThread().map(::threadModelMapper)
        },
    )

    val updateThread = ChannelFlowBuilder.build(
        localAppEndpointService::onUpdateThread,
        remoteAppEndpointService::onUpdateThread,
    )

    val deleteThread = ChannelFlowBuilder.build(
        localAppEndpointService::onDeleteThread,
        remoteAppEndpointService::onDeleteThread,
    )

    val clearThreads = ChannelFlowBuilder.build(
        localAppEndpointService::onClearThreads,
        remoteAppEndpointService::onClearThreads,
    )

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