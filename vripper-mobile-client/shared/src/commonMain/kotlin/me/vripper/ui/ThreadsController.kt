package me.vripper.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import me.vripper.api.EndpointApi
import me.vripper.data.ForumThread

class ThreadsController(
    api: EndpointApi,
    scope: CoroutineScope,
) : ThreadListController(api, scope) {
    override suspend fun loadThreads(): List<ForumThread> = api.findAllThreads()

    override fun observeNewThread(): Flow<ForumThread> = api.onNewThread()

    override fun observeUpdateThread(): Flow<ForumThread> = api.onUpdateThread()

    override fun observeDeleteThread(): Flow<Long> = api.onDeleteThread()

    override fun observeClearThreads(): Flow<Unit> = api.onClearThreads()

    override suspend fun removeThreads(ids: List<Long>) = api.removeThreads(ids)

    override suspend fun clearThreads() = api.clearThreads()

    override val loadErrorMessage = "Unable to load threads"
    override val removeErrorMessage = "Unable to remove threads"
    override val clearErrorMessage = "Unable to clear threads"
}
