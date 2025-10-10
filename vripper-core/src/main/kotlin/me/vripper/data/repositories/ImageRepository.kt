package me.vripper.data.repositories

import me.vripper.entities.ImageEntity
import java.util.*

internal interface ImageRepository {
    fun save(imageEntity: ImageEntity): ImageEntity
    fun save(imageEntityList: List<ImageEntity>)
    fun deleteAllByPostEntityId(postEntityId: Long)
    fun findByPostEntityId(postEntityId: Long): List<ImageEntity>
    fun countError(): Int
    fun findByPostEntityIdAndIsNotCompleted(postEntityId: Long): List<ImageEntity>
    fun stopByPostEntityIdAndIsNotCompleted(postEntityId: Long): Int
    fun stopByPostEntityIdAndIsNotCompleted(): Int
    fun findByPostEntityIdAndIsError(postEntityId: Long): List<ImageEntity>
    fun findById(id: Long): Optional<ImageEntity>
    fun update(imageEntity: ImageEntity)
    fun update(imageEntities: List<ImageEntity>)
    fun deleteAllByPostEntityId(postEntityIds: List<Long>)
}