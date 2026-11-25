package me.vripper.gui.controller

import kotlinx.coroutines.flow.map
import me.vripper.entities.SubscriptionEntity
import me.vripper.gui.model.ThreadModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.GuiErrorReporter
import org.koin.core.component.KoinComponent
import me.vripper.gui.fx.Controller

class SubscriptionController : KoinComponent, Controller() {

    val newSubscription = ChannelFlowBuilder.build(
        { localAppEndpointService.onNewSubscription().map(::threadModelMapper) },
        { remoteAppEndpointService.onNewSubscription().map(::threadModelMapper) },
    )

    val updateSubscription = ChannelFlowBuilder.build(
        localAppEndpointService::onUpdateSubscription,
        remoteAppEndpointService::onUpdateSubscription,
    )

    val deleteSubscription = ChannelFlowBuilder.build(
        localAppEndpointService::onDeleteSubscription,
        remoteAppEndpointService::onDeleteSubscription,
    )

    val clearSubscriptions = ChannelFlowBuilder.build(
        localAppEndpointService::onClearSubscriptions,
        remoteAppEndpointService::onClearSubscriptions,
    )

    suspend fun findAll(): List<ThreadModel> {
        return GuiErrorReporter.call("subscriptions.findAll") {
            currentAppEndpointService().findAllSubscriptions().map(::threadModelMapper)
        }.getOrDefault(emptyList())
    }

    private fun threadModelMapper(it: SubscriptionEntity): ThreadModel {
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
        GuiErrorReporter.call("subscriptions.delete") {
            currentAppEndpointService().subscriptionRemove(threadIdList)
        }
    }

    suspend fun clearAll() {
        GuiErrorReporter.call("subscriptions.clear") {
            currentAppEndpointService().subscriptionClear()
        }
    }

    suspend fun refresh() {
        GuiErrorReporter.call("subscriptions.refresh") {
            currentAppEndpointService().refreshSubscriptions()
        }
    }
}
