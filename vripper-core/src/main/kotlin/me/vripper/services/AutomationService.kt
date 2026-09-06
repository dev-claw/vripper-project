package me.vripper.services

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.entities.PostEntity
import me.vripper.entities.Status
import me.vripper.event.EventBus
import me.vripper.event.PostCompletedEvent
import me.vripper.model.TriggerAction
import me.vripper.model.WebhookMethod
import me.vripper.utilities.ArchiveUtils.zipDirectory
import me.vripper.utilities.HttpClient
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils.moveItem
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

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
                val successTrigger = successCompress && trigger(postEntity)
                if (!successTrigger) {
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

    private suspend fun trigger(postEntity: PostEntity): Boolean {

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

    private suspend fun webhookTrigger(postEntity: PostEntity): Boolean {
        log.info("Executing webhook trigger for ${postEntity.postTitle}")
        when (settingsService.settings.automationSettings.webhookMethod) {
            WebhookMethod.GET -> TODO()
            WebhookMethod.POST -> {
                val response = try {
                    HttpClient.webhookClient.post(
                        replacePlaceholders(
                            settingsService.settings.automationSettings.webhookUrl,
                            postEntity
                        )
                    ) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            replacePlaceholders(
                                settingsService.settings.automationSettings.webhookPayload,
                                postEntity
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.error("Network failure", e)
                    return false
                }

                response.discardRemaining()
                return if (response.status.isSuccess()) {
                    log.info("Webhook successfully executed for ${postEntity.postTitle}")
                    true
                } else {
                    log.error("Failed to trigger webhook for ${postEntity.postTitle}")
                    false
                }
            }
        }
    }

    private fun scriptTrigger(postEntity: PostEntity): Boolean {
        TODO("Not yet implemented")
    }

    private fun replacePlaceholders(value: String, postEntity: PostEntity): String {
        val targetPath = if (settingsService.settings.automationSettings.compress) {
            Path(postEntity.downloadDirectory, postEntity.folderName + ".zip")
        } else {
            Path(postEntity.downloadDirectory, postEntity.folderName)
        }
        val staticReplacement = mapOf("target_path" to targetPath.pathString)
        val metadata = dataAccessService.findMetadataByPostEntityId(postEntity.id).orElse(null)
        val dynamicReplacement = metadata?.data?.customFields?.associate { it.name to it.value } ?: emptyMap()
        return replacePlaceholders(value, staticReplacement, dynamicReplacement)
    }

    fun replacePlaceholders(
        input: String,
        staticReplacements: Map<String, String>,
        customFields: Map<String, String>
    ): String {
        // Regex matches anything inside curly braces, e.g., {target_path} or {cf:name}
        val regex = Regex("\\$\\{([^}]+)}")

        return regex.replace(input) { matchResult ->
            val fullMatch = matchResult.value // Includes the braces, e.g., "{cf:fieldkey}"
            val key = matchResult.groupValues[1] // Inside the braces, e.g., "cf:fieldkey"

            when {
                // Check if it's a dynamic custom field
                key.startsWith("cf:") -> {
                    val fieldKey = key.removePrefix("cf:")
                    // Fetch from dynamic data, fallback to the original placeholder if not found
                    customFields[fieldKey] ?: fullMatch
                }
                // Otherwise, check the static replacements
                else -> {
                    // Fetch from static data, fallback to the original placeholder if not found
                    staticReplacements[key] ?: fullMatch
                }
            }
        }
    }
}
