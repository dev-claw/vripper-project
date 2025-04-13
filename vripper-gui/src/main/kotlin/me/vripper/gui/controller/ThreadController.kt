package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.entities.ThreadEntity
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.model.ThreadSelectionModel
import me.vripper.model.ThreadPostId
import me.vripper.services.IAppEndpointService
import org.koin.core.component.KoinComponent
import tornadofx.Controller

class ThreadController : KoinComponent, Controller() {
    lateinit var appEndpointService: IAppEndpointService

    suspend fun findAll(): List<ThreadModel> {
        return appEndpointService.findAllThreads().map(::threadModelMapper)
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
        appEndpointService.threadRemove(threadIdList)
    }

    suspend fun clearAll() {
        appEndpointService.threadClear()
    }

    suspend fun grab(threadId: Long): List<ThreadSelectionModel> =
        appEndpointService.grab(threadId).map { postItem ->
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
        appEndpointService.download(selectedItems.map {
            ThreadPostId(
                it.threadId,
                it.postId
            )
        })
    }

    fun onNewThread() = appEndpointService.onNewThread().map(::threadModelMapper)

    fun onUpdateThread() = appEndpointService.onUpdateThread()

    fun onDeleteThread() = appEndpointService.onDeleteThread()

    fun onClearThreads() = appEndpointService.onClearThreads()
}