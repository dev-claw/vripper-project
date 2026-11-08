package me.vripper.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.vripper.entities.ActionState
import me.vripper.entities.CustomField
import me.vripper.entities.MetadataEntity
import me.vripper.entities.PostActionEntity
import me.vripper.entities.Status
import me.vripper.event.EventBus
import me.vripper.event.PostActionUpdateEvent
import me.vripper.model.AutomationSettings
import me.vripper.model.TriggerAction
import me.vripper.tasks.PostActionTask
import me.vripper.utilities.LoggerDelegate
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString

internal class PostActionService(
    private val dataAccessService: DataAccessService,
    private val settingsService: SettingsService,
    private val taskService: TaskService,
    private val eventBus: EventBus,
) {
    private val log by LoggerDelegate()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun onPostCompleted(postEntityId: Long) {
        prepare(postEntityId)
    }

    fun prepare(postEntityId: Long): PostActionEntity? {
        return synchronized(postEntityId.toString().intern()) {
            val settings = settingsService.settings.automationSettings
            val existing = dataAccessService.findPostAction(postEntityId)
            if (!settings.isActive()) {
                existing?.let { dataAccessService.deletePostAction(postEntityId) }
                return@synchronized null
            }
            val post = runCatching { dataAccessService.findPostByEntityId(postEntityId) }.getOrNull()
                ?: return@synchronized existing?.also {
                    dataAccessService.updatePostAction(it.copy(state = ActionState.SKIPPED, lastError = "Post no longer exists"))
                }
            if (post.status != Status.FINISHED) {
                return@synchronized existing
            }
            val metadata = dataAccessService.findMetadataByPostEntityId(postEntityId).orElse(null)
            val missing = missingFields(settings, metadata)
            val targetPath = resolvePlaceholders(settings.moveDestination, post, metadata)
            val state = if (settings.mode == me.vripper.model.AutomationMode.AUTO && missing.isEmpty()) {
                ActionState.READY
            } else {
                ActionState.AWAITING_REVIEW
            }
            val now = java.time.LocalDateTime.now()
            val action = PostActionEntity(
                id = existing?.id ?: -1,
                postIdRef = postEntityId,
                action = settings.action,
                state = state,
                attempts = existing?.attempts ?: 0,
                lastError = "",
                missingFields = missing,
                targetPath = targetPath,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            val saved = if (existing == null) {
                dataAccessService.savePostAction(action)
            } else {
                dataAccessService.updatePostAction(action)
            }
            publish(saved)
            if (state == ActionState.READY) {
                run(postEntityId)
            }
            saved
        }
    }

    fun run(postEntityId: Long): Boolean {
        return synchronized(postEntityId.toString().intern()) {
            val settings = settingsService.settings.automationSettings
            if (!settings.isActive()) {
                return@synchronized false
            }
            val action = dataAccessService.findPostAction(postEntityId)
                ?: run {
                    prepare(postEntityId)
                    return@synchronized false
                }
            if (action.state == ActionState.RUNNING) {
                return@synchronized false
            }
            val post = runCatching { dataAccessService.findPostByEntityId(postEntityId) }.getOrNull()
                ?: return@synchronized false
            if (post.status != Status.FINISHED || dataAccessService.countErrorsByPostEntityId(postEntityId) > 0) {
                val failed = dataAccessService.updatePostAction(
                    action.copy(state = ActionState.AWAITING_REVIEW, lastError = "Post is not ready for automation")
                )
                publish(failed)
                return@synchronized false
            }
            val metadata = dataAccessService.findMetadataByPostEntityId(postEntityId).orElse(null)
            val missing = missingFields(settings, metadata)
            if (missing.isNotEmpty()) {
                val blocked = dataAccessService.updatePostAction(
                    action.copy(state = ActionState.AWAITING_REVIEW, missingFields = missing)
                )
                publish(blocked)
                return@synchronized false
            }
            val running = dataAccessService.updatePostAction(
                action.copy(
                    state = ActionState.RUNNING,
                    attempts = action.attempts + 1,
                    lastError = "",
                    missingFields = emptyList(),
                )
            )
            publish(running)
            taskService.submit(PostActionTask(postEntityId))
            true
        }
    }

    fun retry(postEntityId: Long): Boolean {
        val action = dataAccessService.findPostAction(postEntityId) ?: return false
        val ready = dataAccessService.updatePostAction(
            action.copy(state = ActionState.READY, lastError = "")
        )
        publish(ready)
        return run(postEntityId)
    }

    fun skip(postEntityId: Long) {
        val action = dataAccessService.findPostAction(postEntityId) ?: return
        val skipped = dataAccessService.updatePostAction(
            action.copy(state = ActionState.SKIPPED, lastError = "")
        )
        publish(skipped)
        if (settingsService.settings.downloadSettings.clearCompleted) {
            dataAccessService.removeAll(listOf(postEntityId))
        }
    }

    fun updateCustomFields(postEntityId: Long, customFields: List<CustomField>) {
        dataAccessService.updateCustomFields(postEntityId, customFields)
        val action = dataAccessService.findPostAction(postEntityId)
        if (action != null && action.state == ActionState.AWAITING_REVIEW) {
            prepare(postEntityId)
        }
    }

    fun recover() {
        val settings = settingsService.settings.automationSettings
        if (!settings.isActive()) {
            return
        }
        dataAccessService.findNonTerminalPostActions().forEach { action ->
            when (action.state) {
                ActionState.RUNNING, ActionState.READY -> {
                    dataAccessService.updatePostAction(
                        action.copy(state = ActionState.READY, lastError = "Resumed after restart")
                    )
                    prepare(action.postIdRef)
                }

                else -> Unit
            }
        }
    }

    suspend fun execute(postEntityId: Long) {
        withContext(Dispatchers.IO) {
            val action = dataAccessService.findPostAction(postEntityId) ?: return@withContext
            val settings = settingsService.settings.automationSettings
            if (!settings.isActive()) {
                publish(dataAccessService.updatePostAction(action.copy(state = ActionState.SKIPPED)))
                return@withContext
            }
            val post = runCatching { dataAccessService.findPostByEntityId(postEntityId) }.getOrNull()
            if (post == null) {
                publish(
                    dataAccessService.updatePostAction(
                        action.copy(state = ActionState.SKIPPED, lastError = "Post no longer exists")
                    )
                )
                return@withContext
            }
            val metadata = dataAccessService.findMetadataByPostEntityId(postEntityId).orElse(null)
            val missing = missingFields(settings, metadata)
            if (missing.isNotEmpty()) {
                publish(
                    dataAccessService.updatePostAction(
                        action.copy(state = ActionState.AWAITING_REVIEW, missingFields = missing)
                    )
                )
                return@withContext
            }
            try {
                val folder = Path.of(post.downloadDirectory, post.folderName)
                var artifact = folder
                var currentPost = post
                if (settings.compress) {
                    artifact = compress(folder)
                }
                if (settings.action == TriggerAction.MOVE) {
                    val destinationDirectory = resolveMoveDestination(settings, post, metadata)
                    artifact = move(artifact, destinationDirectory, settings.moveOverride)
                    if (!settings.compress) {
                        currentPost = post.copy(
                            downloadDirectory = artifact.parent?.toString() ?: post.downloadDirectory,
                            folderName = artifact.name,
                        )
                        dataAccessService.updatePost(currentPost)
                    }
                }
                when (settings.action) {
                    TriggerAction.WEBHOOK -> callWebhook(settings, currentPost, metadata, artifact)
                    TriggerAction.SCRIPT -> runScript(
                        settings,
                        currentPost,
                        if (settings.compress) folder else artifact,
                        metadata,
                        artifact,
                    )
                    else -> Unit
                }
                val done = dataAccessService.updatePostAction(
                    action.copy(state = ActionState.DONE, lastError = "", missingFields = emptyList())
                )
                publish(done)
                if (settingsService.settings.downloadSettings.clearCompleted) {
                    dataAccessService.removeAll(listOf(postEntityId))
                }
            } catch (e: Exception) {
                log.error("Automation failed for post #$postEntityId", e)
                publish(
                    dataAccessService.updatePostAction(
                        action.copy(state = ActionState.FAILED, lastError = e.message ?: e.toString())
                    )
                )
            }
        }
    }

    private fun missingFields(settings: AutomationSettings, metadata: MetadataEntity?): List<String> {
        val values = metadata?.data?.customFields.orEmpty().associate { it.name to it.value }
        val required = LinkedHashSet(settings.requiredCustomFields.filter { it.isNotBlank() })
        listOf(
            settings.moveDestination,
            settings.webhookUrl,
            settings.webhookPayload,
            settings.scriptPath,
            settings.scriptArguments,
        )
            .forEach { template ->
                placeholderNames(template).filter { it.startsWith("cf:") }
                    .map { it.removePrefix("cf:") }
                    .forEach { required.add(it) }
            }
        return required.filter { values[it].isNullOrBlank() }
    }

    private fun placeholderNames(template: String): List<String> {
        if (template.isBlank()) return emptyList()
        return PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()
    }

    private fun resolveMoveDestination(
        settings: AutomationSettings,
        post: me.vripper.entities.PostEntity,
        metadata: MetadataEntity?,
    ): Path {
        val resolved = resolvePlaceholders(settings.moveDestination, post, metadata)
        if (resolved.isBlank()) {
            throw IllegalArgumentException("Move destination is empty")
        }
        val path = Path.of(resolved)
        return if (path.isAbsolute) path else Path.of(settingsService.settings.downloadSettings.downloadPath).resolve(path)
    }

    private fun resolvePlaceholders(
        template: String,
        post: me.vripper.entities.PostEntity,
        metadata: MetadataEntity?,
        artifact: Path? = null,
    ): String {
        if (template.isBlank()) return template
        val fields = metadata?.data?.customFields.orEmpty().associate { it.name to it.value }
        val folder = Path.of(post.downloadDirectory, post.folderName)
        val artifactPath = artifact
            ?: if (settingsService.settings.automationSettings.compress) {
                folder.resolveSibling("${post.folderName}.zip")
            } else {
                folder
            }
        val progress = if (post.total == 0) 0 else (post.done * 100.0 / post.total).toInt()
        return PLACEHOLDER.replace(template) { match ->
            val key = match.groupValues[1]
            when {
                key == "post.id" -> post.id.toString()
                key == "post.vgPostId" -> post.vgPostId.toString()
                key == "post.title" -> post.postTitle
                key == "post.url" -> post.url
                key == "post.folder" -> post.folderName
                key == "post.directory" -> post.downloadDirectory
                key == "post.path" -> artifactPath.pathString
                key == "post.forum" -> post.forum
                key == "post.thread" -> post.threadTitle
                key == "post.threadId" -> post.vgThreadId.toString()
                key == "post.hosts" -> post.hosts.joinToString(", ")
                key == "post.host" -> post.hosts.firstOrNull().orEmpty()
                key == "post.total" -> post.total.toString()
                key == "post.done" -> post.done.toString()
                key == "post.progress" -> progress.toString()
                key == "post.addedOn" -> post.addedOn.toString()
                key.startsWith("cf:") -> fields[key.removePrefix("cf:")].orEmpty()
                else -> match.value
            }
        }
    }

    private fun move(source: Path, destinationDirectory: Path, override: Boolean): Path {
        if (!source.exists()) {
            throw IllegalStateException("Source path does not exist: $source")
        }
        val fileName = source.fileName ?: throw IllegalStateException("Invalid source path: $source")
        val target = destinationDirectory.resolve(fileName)
        if (source.toAbsolutePath().normalize() == target.toAbsolutePath().normalize()) {
            return source
        }
        Files.createDirectories(destinationDirectory)
        if (target.exists()) {
            if (!override) {
                throw IllegalStateException("Destination already exists: $target")
            }
            deleteRecursively(target)
        }
        return runCatching<Path> {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            runCatching<Path> { Files.move(source, target) }.getOrElse {
                copyThenDelete(source, target)
                target
            }
        }
    }

    private fun copyThenDelete(source: Path, target: Path) {
        if (Files.isDirectory(source)) {
            Files.walk(source).use { paths ->
                paths.forEach { path ->
                    val relative = source.relativize(path)
                    val destination = target.resolve(relative)
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination)
                    } else {
                        destination.parent?.let { Files.createDirectories(it) }
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
        deleteRecursively(source)
    }

    private fun deleteRecursively(path: Path) {
        if (!path.exists()) return
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }

    private fun compress(folder: Path): Path {
        if (!folder.exists() || !folder.isDirectory()) {
            throw IllegalStateException("Post folder does not exist: $folder")
        }
        val zip = folder.resolveSibling("${folder.fileName}.zip")
        Files.deleteIfExists(zip)
        ZipOutputStream(Files.newOutputStream(zip)).use { output ->
            Files.walk(folder).use { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { file ->
                    val entryName = folder.relativize(file).toString().replace(File.separatorChar, '/')
                    output.putNextEntry(ZipEntry(entryName))
                    Files.copy(file, output)
                    output.closeEntry()
                }
            }
        }
        return zip
    }

    private fun callWebhook(
        settings: AutomationSettings,
        post: me.vripper.entities.PostEntity,
        metadata: MetadataEntity?,
        artifact: Path?,
    ) {
        if (settings.webhookUrl.isBlank()) {
            throw IllegalArgumentException("Webhook URL is empty")
        }
        val url = resolvePlaceholders(settings.webhookUrl, post, metadata, artifact)
        val payload = resolvePlaceholders(settings.webhookPayload, post, metadata, artifact)
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
        val request = if (settings.webhookMethod == me.vripper.model.WebhookMethod.GET) {
            builder.GET().build()
        } else {
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
        }
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Webhook returned HTTP ${response.statusCode()}")
        }
    }

    private fun runScript(
        settings: AutomationSettings,
        post: me.vripper.entities.PostEntity,
        folder: Path,
        metadata: MetadataEntity?,
        artifact: Path?,
    ) {
        if (settings.scriptPath.isBlank()) {
            throw IllegalArgumentException("Script path is empty")
        }
        val command = mutableListOf(resolvePlaceholders(settings.scriptPath, post, metadata, artifact))
        command.addAll(splitArguments(resolvePlaceholders(settings.scriptArguments, post, metadata, artifact)))
        val process = ProcessBuilder(command)
            .directory(folder.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("Script exited with code $exitCode: ${output.take(1_000)}")
        }
    }

    private fun splitArguments(arguments: String): List<String> {
        if (arguments.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        arguments.forEach { char ->
            when {
                char == '"' -> quoted = !quoted
                char.isWhitespace() && !quoted -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }

                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun publish(action: PostActionEntity) {
        eventBus.publishEvent(PostActionUpdateEvent(action))
    }

    private companion object {
        val PLACEHOLDER = Regex("\\$\\{([^}]+)}")
    }
}
