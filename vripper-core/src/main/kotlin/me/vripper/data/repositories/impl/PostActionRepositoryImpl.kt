package me.vripper.data.repositories.impl

import kotlinx.serialization.json.Json
import me.vripper.data.repositories.PostActionRepository
import me.vripper.data.tables.PostActionTable
import me.vripper.entities.ActionState
import me.vripper.entities.PostActionEntity
import me.vripper.model.TriggerAction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal class PostActionRepositoryImpl : PostActionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(postAction: PostActionEntity): PostActionEntity {
        val id = PostActionTable.insert {
            it[postIdRef] = postAction.postIdRef
            it[action] = postAction.action.name
            it[state] = postAction.state.name
            it[attempts] = postAction.attempts
            it[lastError] = postAction.lastError
            it[missingFields] = json.encodeToString(postAction.missingFields)
            it[targetPath] = postAction.targetPath
            it[createdAt] = postAction.createdAt
            it[updatedAt] = postAction.updatedAt
        } get PostActionTable.id
        return postAction.copy(id = id.value)
    }

    override fun findByPostId(postIdRef: Long): PostActionEntity? =
        PostActionTable.selectAll().where { PostActionTable.postIdRef eq postIdRef }.map(::transform).firstOrNull()

    override fun findAll(): List<PostActionEntity> =
        PostActionTable.selectAll().map(::transform)

    override fun findNonTerminal(): List<PostActionEntity> =
        PostActionTable.selectAll().where {
            PostActionTable.state inList listOf(
                ActionState.AWAITING_REVIEW.name,
                ActionState.READY.name,
                ActionState.RUNNING.name,
            )
        }.map(::transform)

    override fun update(postAction: PostActionEntity) {
        PostActionTable.update({ PostActionTable.id eq postAction.id }) {
            it[action] = postAction.action.name
            it[state] = postAction.state.name
            it[attempts] = postAction.attempts
            it[lastError] = postAction.lastError
            it[missingFields] = json.encodeToString(postAction.missingFields)
            it[targetPath] = postAction.targetPath
            it[updatedAt] = postAction.updatedAt
        }
    }

    override fun deleteByPostId(postIdRef: Long) {
        PostActionTable.deleteWhere { PostActionTable.postIdRef eq postIdRef }
    }

    override fun deleteAllByPostIds(postIds: List<Long>) {
        postIds.forEach(::deleteByPostId)
    }

    private fun transform(row: ResultRow): PostActionEntity {
        val missingFields = row[PostActionTable.missingFields]
        return PostActionEntity(
            id = row[PostActionTable.id].value,
            postIdRef = row[PostActionTable.postIdRef],
            action = TriggerAction.valueOf(row[PostActionTable.action]),
            state = ActionState.valueOf(row[PostActionTable.state]),
            attempts = row[PostActionTable.attempts],
            lastError = row[PostActionTable.lastError],
            missingFields = if (missingFields.isBlank()) emptyList() else json.decodeFromString(missingFields),
            targetPath = row[PostActionTable.targetPath],
            createdAt = row[PostActionTable.createdAt],
            updatedAt = row[PostActionTable.updatedAt],
        )
    }
}
