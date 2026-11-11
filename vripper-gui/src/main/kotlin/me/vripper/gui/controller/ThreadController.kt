package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.entities.ThreadEntity
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.model.ThreadSelectionModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.GuiErrorReporter
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

    val newThreadPosts = ChannelFlowBuilder.build(
        localAppEndpointService::onThreadNewPosts,
        remoteAppEndpointService::onThreadNewPosts,
    )

    val deleteThread = ChannelFlowBuilder.build(
        localAppEndpointService::onDeleteThread,
        remoteAppEndpointService::onDeleteThread,
    )

    val clearThreads = ChannelFlowBuilder.build(
        localAppEndpointService::onClearThreads,
        remoteAppEndpointService::onClearThreads,
    )

    suspend fun findAll(): List<ThreadModel> {
        return GuiErrorReporter.call("threads.findAll") {
            currentAppEndpointService().findAllThreads().map(::threadModelMapper)
        }.getOrDefault(emptyList())
    }

    private fun threadModelMapper(it: ThreadEntity): ThreadModel {
        return ThreadModel(
            it.title,
            it.link,
            it.total,
            it.threadId,
            it.forum,
            it.starter,
            it.lastPost,
            it.lastPoster,
            it.replyCount,
            it.views,
            it.newestPostId,
            it.sticky,
            it.open,
        )
    }

    suspend fun delete(threadIdList: List<Long>) {
        GuiErrorReporter.call("threads.delete") { currentAppEndpointService().threadRemove(threadIdList) }
    }

    suspend fun clearAll() {
        GuiErrorReporter.call("threads.clear") { currentAppEndpointService().threadClear() }
    }

    suspend fun grab(threadId: Long): List<ThreadSelectionModel> =
        GuiErrorReporter.call("threads.grab") {
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
        }.getOrDefault(emptyList())

    suspend fun download(selectedItems: List<ThreadSelectionModel>) {
        GuiErrorReporter.call("threads.download") {
            currentAppEndpointService().download(selectedItems.map {
                ThreadPostId(
                    it.threadId,
                    it.postId
                )
            })
        }
    }
}