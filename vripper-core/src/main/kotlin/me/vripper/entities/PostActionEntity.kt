package me.vripper.entities

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.vripper.model.TriggerAction
import java.time.LocalDateTime

@Serializable
data class PostActionEntity(
    val id: Long = -1,
    val postIdRef: Long,
    val action: TriggerAction = TriggerAction.NONE,
    val state: ActionState = ActionState.NONE,
    val attempts: Int = 0,
    val lastError: String = "",
    val missingFields: List<String> = emptyList(),
    val targetPath: String = "",
    @Contextual val createdAt: LocalDateTime = LocalDateTime.now(),
    @Contextual val updatedAt: LocalDateTime = LocalDateTime.now(),
)

@Serializable
enum class ActionState {
    NONE, AWAITING_REVIEW, READY, RUNNING, DONE, FAILED, SKIPPED
}
