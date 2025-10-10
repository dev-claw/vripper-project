package me.vripper.data.repositories.impl

import me.vripper.data.repositories.PostRepository
import me.vripper.data.tables.PostTable
import me.vripper.entities.PostEntity
import me.vripper.entities.Status
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

internal class PostRepositoryImpl :
    PostRepository {

    private val delimiter = ";"

    override fun save(postEntities: List<PostEntity>): List<PostEntity> {
        return PostTable.batchInsert(postEntities, shouldReturnGeneratedValues = true) { post ->
            this[PostTable.status] = post.status.name
            this[PostTable.done] = post.done
            this[PostTable.total] = post.total
            this[PostTable.hosts] = post.hosts.joinToString(delimiter)
            this[PostTable.outputPath] = post.downloadDirectory
            this[PostTable.folderName] = post.folderName
            this[PostTable.postId] = post.vgPostId
            this[PostTable.threadId] = post.vgThreadId
            this[PostTable.postTitle] = post.postTitle
            this[PostTable.threadTitle] = post.threadTitle
            this[PostTable.forum] = post.forum
            this[PostTable.url] = post.url
            this[PostTable.token] = post.token
            this[PostTable.addedAt] = post.addedOn
            this[PostTable.size] = post.size
            this[PostTable.downloaded] = post.downloaded
        }.map(::transform)
    }

    override fun findCompleted(): List<Long> {
        return PostTable.select(PostTable.id).where {
            (PostTable.status eq Status.FINISHED.name) and (PostTable.done greaterEq PostTable.total)
        }.map { it[PostTable.id].value }
    }

    override fun findById(id: Long): PostEntity? {
        val result = PostTable.selectAll().where {
            PostTable.id eq id
        }.map { transform(it) }
        return result.firstOrNull()
    }

    override fun findAll(): List<PostEntity> {
        return PostTable.selectAll().map { transform(it) }
    }

    override fun existByPostEntityId(postEntityId: Long): Boolean {
        return PostTable.select(PostTable.id).where { PostTable.id eq postEntityId }.count() > 0
    }

    override fun existByPostId(postId: Long): Boolean {
        return PostTable.select(PostTable.postId).where { PostTable.postId eq postId }.count() > 0
    }

    override fun setDownloadingToStopped(): Int {
        return PostTable.update({ (PostTable.status eq Status.DOWNLOADING.name) or (PostTable.status eq Status.PENDING.name) }) {
            it[status] = Status.STOPPED.name
        }
    }

    override fun deleteByPostEntityId(postEntityId: Long): Int {
        return PostTable.deleteWhere { PostTable.id eq postEntityId }
    }

    override fun update(postEntity: PostEntity) {
        PostTable.update({ PostTable.id eq postEntity.id }) {
            it[status] = postEntity.status.name
            it[done] = postEntity.done
            it[size] = postEntity.size
            it[downloaded] = postEntity.downloaded
            it[folderName] = postEntity.folderName
        }
    }

    override fun update(postEntities: List<PostEntity>) {
        PostTable.batchUpsert(postEntities, shouldReturnGeneratedValues = false) { post ->
            this[PostTable.id] = post.id
            this[PostTable.status] = post.status.name
            this[PostTable.done] = post.done
            this[PostTable.total] = post.total
            this[PostTable.hosts] = post.hosts.joinToString(delimiter)
            this[PostTable.outputPath] = post.downloadDirectory
            this[PostTable.folderName] = post.folderName
            this[PostTable.postId] = post.vgPostId
            this[PostTable.threadId] = post.vgThreadId
            this[PostTable.postTitle] = post.postTitle
            this[PostTable.threadTitle] = post.threadTitle
            this[PostTable.forum] = post.forum
            this[PostTable.url] = post.url
            this[PostTable.token] = post.token
            this[PostTable.size] = post.size
            this[PostTable.downloaded] = post.downloaded
            this[PostTable.addedAt] = post.addedOn
        }
    }

    override fun deleteAll(postEntityIds: List<Long>) {
        val conn = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement("CREATE TEMPORARY TABLE POSTS_DELETE(ID BIGINT PRIMARY KEY)")
            .use {
                it.execute()
            }

        conn.prepareStatement("INSERT INTO POSTS_DELETE VALUES ( ? )").use { ps ->
            postEntityIds.forEach {
                ps.setLong(1, it)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        conn.prepareStatement("DELETE FROM POST WHERE ID IN (SELECT ID FROM POSTS_DELETE)")
            .use {
                it.execute()
            }

        conn.prepareStatement("DELETE FROM POSTS_DELETE").use {
            it.execute()
        }
    }

    override fun stopAll() {
        PostTable.update {
            it[status] = Status.STOPPED.name
        }
    }

    override fun findAllNonCompletedPostEntityIds(): List<Long> {
        return PostTable.select(PostTable.id).where {
            PostTable.status neq Status.FINISHED.name
        }.map { it[PostTable.id].value }
    }

    private fun transform(resultRow: ResultRow): PostEntity {
        val id = resultRow[PostTable.id].value
        val status = Status.valueOf(resultRow[PostTable.status])
        val postId = resultRow[PostTable.postId]
        val threadId = resultRow[PostTable.threadId]
        val postTitle = resultRow[PostTable.postTitle]
        val threadTitle = resultRow[PostTable.threadTitle]
        val forum = resultRow[PostTable.forum]
        val url = resultRow[PostTable.url]
        val token = resultRow[PostTable.token]
        val done = resultRow[PostTable.done]
        val total = resultRow[PostTable.total]
        val hosts =
            resultRow[PostTable.hosts].split(delimiter).dropLastWhile { it.isEmpty() }.toSet()
        val downloadDirectory = resultRow[PostTable.outputPath]
        val folderName = resultRow[PostTable.folderName]
        val addedOn = resultRow[PostTable.addedAt]
        val size = resultRow[PostTable.size]
        val downloaded = resultRow[PostTable.downloaded]
        return PostEntity(
            id,
            postTitle,
            threadTitle,
            forum,
            url,
            token,
            postId,
            threadId,
            total,
            hosts,
            downloadDirectory,
            addedOn,
            folderName,
            status,
            done,
            size,
            downloaded
        )
    }
}
