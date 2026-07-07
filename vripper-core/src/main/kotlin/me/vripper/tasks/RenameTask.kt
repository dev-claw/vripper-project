package me.vripper.tasks

import me.vripper.services.DataAccessService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.io.path.Path
import kotlin.io.path.exists

internal class RenameTask(
    private val postEntityId: Long,
    private val newName: String,
) : KoinComponent, ITask {
    private val log by LoggerDelegate()
    private val dataAccessService by inject<DataAccessService>()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Renaming post"

    override fun getCategory() = TaskCategory.RENAME

    override fun getDescription() = "Renaming post #$postEntityId to \"$newName\""

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        try {
            synchronized(postEntityId.toString().intern()) {
                if (!dataAccessService.exists(postEntityId)) {
                    status = TaskStatus.WARNING
                    result = "Post #$postEntityId no longer exists"
                    return
                }
                dataAccessService.findPostByEntityId(postEntityId).let { post ->
                    if (Path(post.downloadDirectory, post.folderName).exists()) {
                        PathUtils.rename(
                            dataAccessService.findImagesByPostEntityId(postEntityId),
                            post.downloadDirectory,
                            post.folderName,
                            newName
                        )
                    }
                    post.folderName = PathUtils.sanitize(newName)
                    dataAccessService.updatePost(post)
                }
                status = TaskStatus.SUCCESS
                result = "Renamed post #$postEntityId to \"$newName\""
            }
        } catch (e: Exception) {
            status = TaskStatus.FAILURE
            result = "Failed to rename post #$postEntityId: ${e.message ?: e.toString()}"
            log.error("Failed to rename post #$postEntityId", e)
        }
    }
}
