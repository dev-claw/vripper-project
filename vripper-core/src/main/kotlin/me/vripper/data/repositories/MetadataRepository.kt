package me.vripper.data.repositories

import me.vripper.entities.MetadataEntity
import java.util.*

internal interface MetadataRepository {
    fun save(metadataEntity: MetadataEntity): MetadataEntity
    fun findByPostEntityId(postEntityId: Long): Optional<MetadataEntity>
    fun deleteByPostEntityId(postEntityId: Long): Int
    fun deleteAllByPostEntityId(postEntityIds: List<Long>)
}