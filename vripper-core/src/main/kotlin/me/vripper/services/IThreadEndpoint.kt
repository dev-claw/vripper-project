package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.event.ThreadNewPostsEvent
import me.vripper.model.PostSelection
import me.vripper.model.Subscription
import me.vripper.model.Thread

interface IThreadEndpoint {
    fun onNewThread(): Flow<Thread>
    fun onUpdateThread(): Flow<Thread>
    fun onDeleteThread(): Flow<Long>
    fun onClearThreads(): Flow<Unit>
    fun onThreadNewPosts(): Flow<ThreadNewPostsEvent>
    suspend fun findAllThreads(): List<Thread>
    suspend fun threadRemove(threadIdList: List<Long>)
    suspend fun threadClear()
    fun onNewSubscription(): Flow<Subscription>
    fun onUpdateSubscription(): Flow<Subscription>
    fun onDeleteSubscription(): Flow<Long>
    fun onClearSubscriptions(): Flow<Unit>
    suspend fun findAllSubscriptions(): List<Subscription>
    suspend fun subscriptionRemove(threadIdList: List<Long>)
    suspend fun subscriptionClear()
    suspend fun refreshSubscriptions()
    suspend fun grab(threadId: Long): List<PostSelection>
}
