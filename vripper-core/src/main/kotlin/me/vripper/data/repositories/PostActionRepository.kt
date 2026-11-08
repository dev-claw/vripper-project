package me.vripper.data.repositories

import me.vripper.entities.PostActionEntity

internal interface PostActionRepository {
    fun save(postAction: PostActionEntity): PostActionEntity
    fun findByPostId(postIdRef: Long): PostActionEntity?
    fun findAll(): List<PostActionEntity>
    fun findNonTerminal(): List<PostActionEntity>
    fun update(postAction: PostActionEntity)
    fun deleteByPostId(postIdRef: Long)
    fun deleteAllByPostIds(postIds: List<Long>)
}
