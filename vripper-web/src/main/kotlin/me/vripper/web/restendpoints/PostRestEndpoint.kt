package me.vripper.web.restendpoints

import kotlinx.coroutines.runBlocking
import me.vripper.entities.PostActionEntity
import me.vripper.model.PostSelection
import me.vripper.model.ThreadPostId
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.LoggerDelegate
import me.vripper.web.restendpoints.domain.CustomFieldsRequest
import me.vripper.web.restendpoints.domain.PostActionRequest
import me.vripper.web.restendpoints.domain.RenameRequest
import me.vripper.web.restendpoints.domain.ScanRequest
import me.vripper.web.restendpoints.exceptions.BadRequestException
import me.vripper.web.restendpoints.exceptions.ServerErrorException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class PostRestEndpoint : KoinComponent {

    private val log by LoggerDelegate()
    private val appEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))

    @PostMapping("/post")
    @ResponseStatus(code = HttpStatus.OK)
    fun scan(@RequestBody scanRequest: ScanRequest) {
        if (scanRequest.links.isNullOrBlank()) {
            log.error("Cannot process empty requests")
            throw BadRequestException("Cannot process empty requests")
        }
        runBlocking { appEndpointService.scanLinks(scanRequest.links) }
    }

    @PostMapping("/post/restart")
    @ResponseStatus(value = HttpStatus.OK)
    fun start(@RequestBody postIds: List<Long>) {
        runBlocking { appEndpointService.restartAll(postIds) }
    }

    @PostMapping("/post/add")
    @ResponseStatus(value = HttpStatus.OK)
    fun download(@RequestBody posts: List<ThreadPostId>) {
        runBlocking { appEndpointService.download(posts) }
    }

    @PostMapping("/post/restart/all")
    @ResponseStatus(value = HttpStatus.OK)
    fun startAll() {
        runBlocking { appEndpointService.restartAll() }
    }

    @PostMapping("/post/stop")
    @ResponseStatus(value = HttpStatus.OK)
    fun stop(@RequestBody postIds: List<Long>) {
        runBlocking { appEndpointService.stopAll(postIds) }
    }

    @PostMapping("/post/stop/all")
    @ResponseStatus(value = HttpStatus.OK)
    fun stopAll() {
        runBlocking { appEndpointService.stopAll() }
    }

    @PostMapping("/post/remove")
    @ResponseStatus(value = HttpStatus.OK)
    fun remove(@RequestBody postIds: List<Long>): List<Long> {
        runBlocking { appEndpointService.remove(postIds) }
        return postIds
    }

    @PostMapping("/post/clear/all")
    @ResponseStatus(value = HttpStatus.OK)
    fun clearAll(): List<Long> {
        return runBlocking { appEndpointService.clearCompleted() }
    }

    @GetMapping("/grab/{threadId}")
    @ResponseStatus(value = HttpStatus.OK)
    fun grab(@PathVariable("threadId") threadId: Long): List<PostSelection> {
        return try {
            runBlocking { appEndpointService.grab(threadId) }
        } catch (e: Exception) {
            throw ServerErrorException(
                String.format(
                    "Failed to get links for threadId = %s, %s",
                    threadId,
                    e.message
                )
            )
        }
    }

    @PostMapping("/grab/remove")
    @ResponseStatus(value = HttpStatus.OK)
    fun grabRemove(@RequestBody threadIds: List<Long>) {
        runBlocking { appEndpointService.threadRemove(threadIds) }
    }

    @GetMapping("/grab/clear")
    @ResponseStatus(value = HttpStatus.OK)
    fun threadClear() {
        runBlocking { appEndpointService.threadClear() }
    }

    @PostMapping("/post/rename")
    @ResponseStatus(value = HttpStatus.OK)
    fun rename(@RequestBody renameRequest: RenameRequest) {
        runBlocking { appEndpointService.rename(renameRequest.postId, renameRequest.name) }
    }

    @GetMapping("/post/{postId}/action")
    @ResponseStatus(value = HttpStatus.OK)
    fun findAction(@PathVariable("postId") postId: Long): PostActionEntity? {
        return runBlocking { appEndpointService.findPostAction(postId) }
    }

    @GetMapping("/post/actions")
    @ResponseStatus(value = HttpStatus.OK)
    fun findAllActions(): List<PostActionEntity> {
        return runBlocking { appEndpointService.findAllPostActions() }
    }

    @PostMapping("/post/action/run")
    @ResponseStatus(value = HttpStatus.OK)
    fun runAction(@RequestBody request: PostActionRequest) {
        runBlocking { appEndpointService.runPostAction(request.postId) }
    }

    @PostMapping("/post/action/retry")
    @ResponseStatus(value = HttpStatus.OK)
    fun retryAction(@RequestBody request: PostActionRequest) {
        runBlocking { appEndpointService.retryPostAction(request.postId) }
    }

    @PostMapping("/post/action/skip")
    @ResponseStatus(value = HttpStatus.OK)
    fun skipAction(@RequestBody request: PostActionRequest) {
        runBlocking { appEndpointService.skipPostAction(request.postId) }
    }

    @PostMapping("/post/custom-fields")
    @ResponseStatus(value = HttpStatus.OK)
    fun updateCustomFields(@RequestBody request: CustomFieldsRequest) {
        runBlocking { appEndpointService.updateCustomFields(request.postId, request.customFields) }
    }
}