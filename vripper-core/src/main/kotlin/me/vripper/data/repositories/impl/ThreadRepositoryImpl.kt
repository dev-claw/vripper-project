package me.vripper.data.repositories.impl

import me.vripper.data.repositories.ThreadRepository
import me.vripper.data.tables.ThreadTable
import me.vripper.entities.ThreadEntity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

internal class ThreadRepositoryImpl : ThreadRepository {

    override fun save(threadEntity: ThreadEntity): ThreadEntity {
        val id = ThreadTable.insertAndGetId {
            it[title] = threadEntity.title
            it[total] = threadEntity.total
            it[url] = threadEntity.link
            it[threadId] = threadEntity.threadId
            it[forumId] = threadEntity.forumId
            it[forum] = threadEntity.forum
            it[prefixId] = threadEntity.prefixId
            it[starter] = threadEntity.starter
            it[starterId] = threadEntity.starterId
            it[created] = threadEntity.created
            it[lastPost] = threadEntity.lastPost
            it[lastPostId] = threadEntity.lastPostId
            it[lastPoster] = threadEntity.lastPoster
            it[replyCount] = threadEntity.replyCount
            it[views] = threadEntity.views
            it[open] = threadEntity.open
            it[sticky] = threadEntity.sticky
            it[newestPostId] = threadEntity.newestPostId
        }.value
        return threadEntity.copy(id = id)
    }

    override fun update(threadEntity: ThreadEntity) {
        ThreadTable.update({ ThreadTable.id eq threadEntity.id }) {
            it[title] = threadEntity.title
            it[total] = threadEntity.total
            it[url] = threadEntity.link
            it[forumId] = threadEntity.forumId
            it[forum] = threadEntity.forum
            it[prefixId] = threadEntity.prefixId
            it[starter] = threadEntity.starter
            it[starterId] = threadEntity.starterId
            it[created] = threadEntity.created
            it[lastPost] = threadEntity.lastPost
            it[lastPostId] = threadEntity.lastPostId
            it[lastPoster] = threadEntity.lastPoster
            it[replyCount] = threadEntity.replyCount
            it[views] = threadEntity.views
            it[open] = threadEntity.open
            it[sticky] = threadEntity.sticky
            it[newestPostId] = threadEntity.newestPostId
        }
    }

    override fun findByThreadId(threadId: Long): Optional<ThreadEntity> {
        val result = ThreadTable.selectAll().where {
            ThreadTable.threadId eq threadId
        }.map(this::transform)
        return if (result.isEmpty()) {
            Optional.empty()
        } else {
            Optional.of(result.first())
        }
    }

    override fun findAll(): List<ThreadEntity> {
        return ThreadTable.selectAll().map(this::transform)
    }

    override fun findById(id: Long): Optional<ThreadEntity> {
        val result = ThreadTable.selectAll().where {
            ThreadTable.id eq id
        }.map(this::transform)
        return if (result.isEmpty()) {
            Optional.empty()
        } else {
            Optional.of(result.first())
        }
    }

    override fun deleteByThreadId(threadId: Long): Int {
        return ThreadTable.deleteWhere { ThreadTable.threadId eq threadId }
    }

    override fun deleteAll() {
        ThreadTable.deleteAll()
    }

    private fun transform(resultRow: ResultRow): ThreadEntity {
        return ThreadEntity(
            id = resultRow[ThreadTable.id].value,
            title = resultRow[ThreadTable.title],
            link = resultRow[ThreadTable.url],
            threadId = resultRow[ThreadTable.threadId],
            total = resultRow[ThreadTable.total],
            forumId = resultRow[ThreadTable.forumId] ?: -1,
            forum = resultRow[ThreadTable.forum] ?: "",
            prefixId = resultRow[ThreadTable.prefixId] ?: "",
            starter = resultRow[ThreadTable.starter] ?: "",
            starterId = resultRow[ThreadTable.starterId] ?: -1,
            created = resultRow[ThreadTable.created] ?: "",
            lastPost = resultRow[ThreadTable.lastPost] ?: "",
            lastPostId = resultRow[ThreadTable.lastPostId] ?: -1,
            lastPoster = resultRow[ThreadTable.lastPoster] ?: "",
            replyCount = resultRow[ThreadTable.replyCount] ?: 0,
            views = resultRow[ThreadTable.views] ?: 0,
            open = resultRow[ThreadTable.open] ?: true,
            sticky = resultRow[ThreadTable.sticky] ?: false,
            newestPostId = resultRow[ThreadTable.newestPostId] ?: 0,
        )
    }
}
