package me.vripper.data.repositories.impl

import me.vripper.data.repositories.ImageRepository
import me.vripper.data.tables.ImageTable
import me.vripper.entities.ImageEntity
import me.vripper.entities.Status
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import java.util.*

internal class ImageRepositoryImpl : ImageRepository {
    override fun save(imageEntity: ImageEntity): ImageEntity {
        val id = ImageTable.insertAndGetId {
            it[downloaded] = imageEntity.downloaded
            it[host] = imageEntity.host
            it[index] = imageEntity.index
            it[status] = imageEntity.status.name
            it[size] = imageEntity.size
            it[url] = imageEntity.url
            it[thumbUrl] = imageEntity.thumbUrl
            it[postIdRef] = imageEntity.postEntityId
            it[filename] = imageEntity.filename
        }.value
        return imageEntity.copy(id = id)
    }

    override fun save(imageEntityList: List<ImageEntity>) {
        ImageTable.batchInsert(imageEntityList, shouldReturnGeneratedValues = false) {
            this[ImageTable.downloaded] = it.downloaded
            this[ImageTable.host] = it.host
            this[ImageTable.index] = it.index
            this[ImageTable.status] = it.status.name
            this[ImageTable.size] = it.size
            this[ImageTable.url] = it.url
            this[ImageTable.thumbUrl] = it.thumbUrl
            this[ImageTable.postIdRef] = it.postEntityId
            this[ImageTable.filename] = it.filename
        }
    }

    override fun deleteAllByPostEntityId(postEntityId: Long) {
        ImageTable.deleteWhere { ImageTable.postIdRef eq postEntityId }
    }

    override fun findByPostEntityId(postEntityId: Long): List<ImageEntity> {
        return ImageTable.selectAll().where {
            ImageTable.postIdRef eq postEntityId
        }.map(this::transform)
    }

    override fun countError(): Int {
        return ImageTable
            .select(ImageTable.id)
            .where { ImageTable.status eq Status.ERROR.name }
            .count().toInt()
    }

    override fun findByPostEntityIdAndIsNotCompleted(postEntityId: Long): List<ImageEntity> {
        return ImageTable
            .selectAll().where {
                (ImageTable.postIdRef eq postEntityId) and (ImageTable.status neq Status.FINISHED.name)
            }.map(this::transform)
    }

    override fun stopByPostEntityIdAndIsNotCompleted(postEntityId: Long): Int {
        return ImageTable.update({ (ImageTable.postIdRef eq postEntityId) and (ImageTable.status neq Status.FINISHED.name) }) {
            it[status] = Status.STOPPED.name
        }
    }

    override fun stopByPostEntityIdAndIsNotCompleted(): Int {
        return ImageTable.update({ (ImageTable.status neq Status.FINISHED.name) }) {
            it[status] = Status.STOPPED.name
        }
    }

    override fun findByPostEntityIdAndIsError(postEntityId: Long): List<ImageEntity> {
        return ImageTable.selectAll().where {
            (ImageTable.postIdRef eq postEntityId) and (ImageTable.status eq Status.ERROR.name)
        }.map(this::transform)
    }

    override fun findById(id: Long): Optional<ImageEntity> {
        val result = ImageTable.selectAll().where {
            ImageTable.id eq id
        }.map(this::transform)
        return if (result.isEmpty()) {
            Optional.empty()
        } else {
            Optional.of(result.first())
        }
    }

    override fun update(imageEntity: ImageEntity) {
        ImageTable.update({ ImageTable.id eq imageEntity.id }) {
            it[status] = imageEntity.status.name
            it[downloaded] = imageEntity.downloaded
            it[size] = imageEntity.size
            it[filename] = imageEntity.filename
        }
    }

    override fun update(imageEntities: List<ImageEntity>) {
        ImageTable.batchUpsert(imageEntities, shouldReturnGeneratedValues = false) {
            this[ImageTable.id] = it.id
            this[ImageTable.downloaded] = it.downloaded
            this[ImageTable.host] = it.host
            this[ImageTable.index] = it.index
            this[ImageTable.status] = it.status.name
            this[ImageTable.size] = it.size
            this[ImageTable.url] = it.url
            this[ImageTable.thumbUrl] = it.thumbUrl
            this[ImageTable.postIdRef] = it.postEntityId
            this[ImageTable.filename] = it.filename
        }
    }

    override fun deleteAllByPostEntityId(postEntityIds: List<Long>) {
        val conn = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement("CREATE TEMPORARY TABLE IMAGES_DELETE(POST_ID_REF BIGINT PRIMARY KEY)")
            .use {
                it.execute()
            }

        conn.prepareStatement("INSERT INTO IMAGES_DELETE VALUES ( ? )").use { ps ->
            postEntityIds.forEach {
                ps.setLong(1, it)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        conn.prepareStatement("DELETE FROM IMAGE WHERE POST_ID_REF IN (SELECT POST_ID_REF FROM IMAGES_DELETE)")
            .use {
                it.execute()
            }

        conn.prepareStatement("DELETE FROM IMAGES_DELETE").use {
            it.execute()
        }
    }

    private fun transform(resultRow: ResultRow): ImageEntity {
        val id = resultRow[ImageTable.id].value
        val url = resultRow[ImageTable.url]
        val thumbUrl = resultRow[ImageTable.thumbUrl]
        val host = resultRow[ImageTable.host]
        val index = resultRow[ImageTable.index]
        val current = resultRow[ImageTable.downloaded]
        val total = resultRow[ImageTable.size]
        val status = Status.valueOf(resultRow[ImageTable.status])
        val postIdRef = resultRow[ImageTable.postIdRef]
        val filename = resultRow[ImageTable.filename]
        return ImageEntity(
            id,
            url,
            thumbUrl,
            host,
            index,
            postIdRef,
            total,
            current,
            status,
            filename
        )
    }
}
