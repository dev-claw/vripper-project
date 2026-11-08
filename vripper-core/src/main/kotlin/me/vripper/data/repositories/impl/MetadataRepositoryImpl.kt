package me.vripper.data.repositories.impl

import kotlinx.serialization.json.Json
import me.vripper.data.repositories.MetadataRepository
import me.vripper.data.tables.MetadataTable
import me.vripper.entities.MetadataEntity
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.sql.Connection
import java.util.*

internal class MetadataRepositoryImpl : MetadataRepository {

    override fun save(metadataEntity: MetadataEntity): MetadataEntity {
        val encoded = Json.encodeToString(metadataEntity.data)
        val exists = MetadataTable.selectAll().where {
            MetadataTable.postIdRef eq metadataEntity.postIdRef
        }.any()
        if (exists) {
            MetadataTable.update({ MetadataTable.postIdRef eq metadataEntity.postIdRef }) {
                it[data] = encoded
            }
        } else {
            MetadataTable.insert {
                it[postIdRef] = metadataEntity.postIdRef
                it[data] = encoded
            }
        }

        return metadataEntity
    }

    override fun findAll(): List<MetadataEntity> {
        return MetadataTable.selectAll().map(::transform)
    }

    override fun findByPostEntityId(postEntityId: Long): Optional<MetadataEntity> {

        val result = MetadataTable.selectAll().where {
            MetadataTable.postIdRef eq postEntityId
        }.map(::transform)

        return if (result.isEmpty()) {
            Optional.empty()
        } else {
            Optional.of(result.first())
        }
    }

    private fun transform(row: ResultRow): MetadataEntity {
        val postEntityId = row[MetadataTable.postIdRef]
        val data = Json.decodeFromString(row[MetadataTable.data]) as MetadataEntity.Data
        return MetadataEntity(postEntityId, data)
    }

    override fun deleteByPostEntityId(postEntityId: Long): Int {
        return MetadataTable.deleteWhere { MetadataTable.postIdRef eq postEntityId }
    }

    override fun deleteAllByPostEntityId(postEntityIds: List<Long>) {
        val conn = TransactionManager.current().connection.connection as Connection
        conn.prepareStatement("CREATE TEMPORARY TABLE METADATA_DELETE(POST_ID_REF BIGINT PRIMARY KEY)")
            .use {
                it.execute()
            }

        conn.prepareStatement("INSERT INTO METADATA_DELETE VALUES ( ? )").use { ps ->
            postEntityIds.forEach {
                ps.setLong(1, it)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        conn.prepareStatement("DELETE FROM METADATA WHERE POST_ID_REF IN (SELECT POST_ID_REF FROM METADATA_DELETE)")
            .use {
                it.execute()
            }

        conn.prepareStatement("DELETE FROM METADATA_DELETE").use {
            it.execute()
        }
    }
}
