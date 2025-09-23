package me.vripper.data.repositories

import me.vripper.entities.PostEntity

internal interface PostRepository {
    fun save(postEntities: List<PostEntity>): List<PostEntity>
    fun findById(id: Long): PostEntity?
    fun findCompleted(): List<Long>
    fun findAll(): List<PostEntity>
    fun existByPostEntityId(postEntityId: Long): Boolean
    fun existByPostId(postId: Long): Boolean
    fun setDownloadingToStopped(): Int
    fun deleteByPostEntityId(postEntityId: Long): Int
    fun update(postEntity: PostEntity)
    fun update(postEntities: List<PostEntity>)
    fun deleteAll(postEntityIds: List<Long>)
    fun stopAll()
    fun findAllNonCompletedPostEntityIds(): List<Long>
}