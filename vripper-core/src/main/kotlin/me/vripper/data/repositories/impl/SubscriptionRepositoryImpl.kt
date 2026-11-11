package me.vripper.data.repositories.impl

import me.vripper.data.repositories.SubscriptionRepository
import me.vripper.data.tables.SubscriptionTable
import me.vripper.entities.SubscriptionEntity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

internal class SubscriptionRepositoryImpl : SubscriptionRepository {

    override fun save(subscriptionEntity: SubscriptionEntity): SubscriptionEntity {
        val id = SubscriptionTable.insertAndGetId {
            it[title] = subscriptionEntity.title
            it[total] = subscriptionEntity.total
            it[url] = subscriptionEntity.link
            it[threadId] = subscriptionEntity.threadId
            it[forumId] = subscriptionEntity.forumId
            it[forum] = subscriptionEntity.forum
            it[prefixId] = subscriptionEntity.prefixId
            it[starter] = subscriptionEntity.starter
            it[starterId] = subscriptionEntity.starterId
            it[created] = subscriptionEntity.created
            it[lastPost] = subscriptionEntity.lastPost
            it[lastPostId] = subscriptionEntity.lastPostId
            it[lastPoster] = subscriptionEntity.lastPoster
            it[replyCount] = subscriptionEntity.replyCount
            it[views] = subscriptionEntity.views
            it[open] = subscriptionEntity.open
            it[sticky] = subscriptionEntity.sticky
            it[newestPostId] = subscriptionEntity.newestPostId
        }.value
        return subscriptionEntity.copy(id = id)
    }

    override fun update(subscriptionEntity: SubscriptionEntity) {
        SubscriptionTable.update({ SubscriptionTable.id eq subscriptionEntity.id }) {
            it[title] = subscriptionEntity.title
            it[total] = subscriptionEntity.total
            it[url] = subscriptionEntity.link
            it[forumId] = subscriptionEntity.forumId
            it[forum] = subscriptionEntity.forum
            it[prefixId] = subscriptionEntity.prefixId
            it[starter] = subscriptionEntity.starter
            it[starterId] = subscriptionEntity.starterId
            it[created] = subscriptionEntity.created
            it[lastPost] = subscriptionEntity.lastPost
            it[lastPostId] = subscriptionEntity.lastPostId
            it[lastPoster] = subscriptionEntity.lastPoster
            it[replyCount] = subscriptionEntity.replyCount
            it[views] = subscriptionEntity.views
            it[open] = subscriptionEntity.open
            it[sticky] = subscriptionEntity.sticky
            it[newestPostId] = subscriptionEntity.newestPostId
        }
    }

    override fun findByThreadId(threadId: Long): Optional<SubscriptionEntity> {
        val result = SubscriptionTable.selectAll().where {
            SubscriptionTable.threadId eq threadId
        }.map(this::transform)
        return if (result.isEmpty()) Optional.empty() else Optional.of(result.first())
    }

    override fun findAll(): List<SubscriptionEntity> {
        return SubscriptionTable.selectAll().map(this::transform)
    }

    override fun deleteByThreadId(threadId: Long): Int {
        return SubscriptionTable.deleteWhere { SubscriptionTable.threadId eq threadId }
    }

    override fun deleteAll() {
        SubscriptionTable.deleteAll()
    }

    private fun transform(resultRow: ResultRow): SubscriptionEntity {
        return SubscriptionEntity(
            id = resultRow[SubscriptionTable.id].value,
            title = resultRow[SubscriptionTable.title],
            link = resultRow[SubscriptionTable.url],
            threadId = resultRow[SubscriptionTable.threadId],
            total = resultRow[SubscriptionTable.total],
            forumId = resultRow[SubscriptionTable.forumId] ?: -1,
            forum = resultRow[SubscriptionTable.forum] ?: "",
            prefixId = resultRow[SubscriptionTable.prefixId] ?: "",
            starter = resultRow[SubscriptionTable.starter] ?: "",
            starterId = resultRow[SubscriptionTable.starterId] ?: -1,
            created = resultRow[SubscriptionTable.created] ?: "",
            lastPost = resultRow[SubscriptionTable.lastPost] ?: "",
            lastPostId = resultRow[SubscriptionTable.lastPostId] ?: -1,
            lastPoster = resultRow[SubscriptionTable.lastPoster] ?: "",
            replyCount = resultRow[SubscriptionTable.replyCount] ?: 0,
            views = resultRow[SubscriptionTable.views] ?: 0,
            open = resultRow[SubscriptionTable.open] ?: true,
            sticky = resultRow[SubscriptionTable.sticky] ?: false,
            newestPostId = resultRow[SubscriptionTable.newestPostId] ?: 0,
        )
    }
}
