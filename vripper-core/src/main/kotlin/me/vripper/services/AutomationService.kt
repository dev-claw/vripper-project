package me.vripper.services

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.entities.PostEntity
import me.vripper.entities.Status
import me.vripper.event.EventBus
import me.vripper.event.PostCompletedEvent
import me.vripper.model.TriggerAction
import me.vripper.utilities.ArchiveUtils.zipDirectory
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils.moveItem
import java.nio.file.Path
import kotlin.io.path.Path

internal class AutomationService(
    private val eventBus: EventBus,
    private val dataAccessService: DataAccessService,
    private val settingsService: SettingsService,
) {

    private val log by LoggerDelegate()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun init() {
        job?.cancel()
        job = coroutineScope.launch {
            eventBus.events.filterIsInstance(PostCompletedEvent::class).collect {
                val postEntity = dataAccessService.findPostByEntityId(it.postEntityId)
                val successCompress = compress(postEntity)
                val successTrigger = trigger(postEntity)
                if (!successCompress || !successTrigger) {
                    dataAccessService.updatePost(postEntity.copy(status = Status.ERROR))
                } else {
                    dataAccessService.updatePost(postEntity.copy(status = Status.FINISHED))
                }
            }
        }
    }

    fun halt() {
        job?.cancel()
    }

    private fun compress(postEntity: PostEntity): Boolean {

        return if (settingsService.settings.automationSettings.compress) {
            dataAccessService.updatePost(postEntity.copy(status = Status.AUTOMATION))
            val sourcePath = Path(postEntity.downloadDirectory, postEntity.folderName)
            log.info("Zipping $sourcePath")
            runCatching {
                zipDirectory(sourcePath)
            }.fold({
                true
            }, {
                log.error("Failed to zip directory $sourcePath", it)
                false
            })
        } else {
            true
        }
    }

    private fun trigger(postEntity: PostEntity): Boolean {

        return if (settingsService.settings.automationSettings.trigger) {

            val sourcePath = if (settingsService.settings.automationSettings.compress) {
                Path(postEntity.downloadDirectory, postEntity.folderName + ".zip")
            } else {
                Path(postEntity.downloadDirectory, postEntity.folderName)
            }

            when (settingsService.settings.automationSettings.triggerAction) {
                TriggerAction.Move -> moveTrigger(sourcePath, postEntity)
                TriggerAction.Webhook -> webhookTrigger(postEntity)
                TriggerAction.Script -> scriptTrigger(postEntity)
            }
        } else {
            true
        }


    }

    private fun moveTrigger(sourcePath: Path, postEntity: PostEntity): Boolean {
        dataAccessService.updatePost(postEntity.copy(status = Status.AUTOMATION))
        val destinationPath = Path(settingsService.settings.automationSettings.moveDestination)
        log.info("Moving $sourcePath to $destinationPath")
        return runCatching {
            moveItem(sourcePath, destinationPath, settingsService.settings.automationSettings.moveOverride)
        }.fold({
            true
        }, {
            log.error("Failed to move $sourcePath to $destinationPath", it)
            false
        })
    }

    private fun webhookTrigger(postEntity: PostEntity): Boolean {
        TODO("Not yet implemented")
    }

    private fun scriptTrigger(postEntity: PostEntity): Boolean {
        TODO("Not yet implemented")
    }
}
