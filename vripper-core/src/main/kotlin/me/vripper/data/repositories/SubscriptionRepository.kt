package me.vripper.data.repositories

import me.vripper.entities.SubscriptionEntity
import java.util.*

internal interface SubscriptionRepository {
    fun save(subscriptionEntity: SubscriptionEntity): SubscriptionEntity
    fun update(subscriptionEntity: SubscriptionEntity)
    fun findByThreadId(threadId: Long): Optional<SubscriptionEntity>
    fun findAll(): List<SubscriptionEntity>
    fun deleteByThreadId(threadId: Long): Int
    fun deleteAll()
}
