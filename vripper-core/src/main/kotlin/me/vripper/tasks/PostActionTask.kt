package me.vripper.tasks

import me.vripper.services.DataAccessService
import me.vripper.services.PostActionService
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class PostActionTask(
    private val postEntityId: Long,
) : KoinComponent, ITask {
    private val log by LoggerDelegate()
    private val dataAccessService by inject<DataAccessService>()
    private val postActionService by inject<PostActionService>()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Running post automation"

    override fun getCategory() = TaskCategory.AUTOMATION

    override fun getPostEntityId() = postEntityId

    override fun getDescription() = "Running automation for post #$postEntityId"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        try {
            if (!dataAccessService.exists(postEntityId)) {
                status = TaskStatus.WARNING
                result = "Post #$postEntityId no longer exists"
                return
            }
            postActionService.execute(postEntityId)
            val action = dataAccessService.findPostAction(postEntityId)
            result = action?.lastError?.takeIf { it.isNotBlank() } ?: "Automation completed for post #$postEntityId"
            status = when (action?.state) {
                me.vripper.entities.ActionState.FAILED -> TaskStatus.FAILURE
                me.vripper.entities.ActionState.AWAITING_REVIEW -> TaskStatus.WARNING
                else -> TaskStatus.SUCCESS
            }
        } catch (e: Exception) {
            status = TaskStatus.FAILURE
            result = "Automation failed for post #$postEntityId: ${e.message ?: e.toString()}"
            log.error("Automation failed for post #$postEntityId", e)
        }
    }
}
