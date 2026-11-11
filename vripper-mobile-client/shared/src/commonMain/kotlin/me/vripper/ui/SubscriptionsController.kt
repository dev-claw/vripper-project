package me.vripper.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import me.vripper.api.EndpointApi
import me.vripper.data.ForumThread

class SubscriptionsController(
    api: EndpointApi,
    scope: CoroutineScope,
) : ThreadListController(api, scope) {
    override suspend fun loadThreads(): List<ForumThread> = api.findAllSubscriptions()

    override fun observeNewThread(): Flow<ForumThread> = api.onNewSubscription()

    override fun observeUpdateThread(): Flow<ForumThread> = api.onUpdateSubscription()

    override fun observeDeleteThread(): Flow<Long> = api.onDeleteSubscription()

    override fun observeClearThreads(): Flow<Unit> = api.onClearSubscriptions()

    override suspend fun removeThreads(ids: List<Long>) = api.removeSubscriptions(ids)

    override suspend fun clearThreads() = api.clearSubscriptions()

    override val loadErrorMessage = "Unable to load subscriptions"
    override val removeErrorMessage = "Unable to remove subscriptions"
    override val clearErrorMessage = "Unable to clear subscriptions"
}
