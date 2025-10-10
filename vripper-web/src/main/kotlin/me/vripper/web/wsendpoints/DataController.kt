package me.vripper.web.wsendpoints

import kotlinx.coroutines.runBlocking
import me.vripper.model.*
import me.vripper.services.IAppEndpointService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller

@Controller
class DataController : KoinComponent {
    private val appEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))

    @SubscribeMapping("/queue-state")
    fun queueState(): QueueState {
        return QueueState(0, 0)
    }

    @SubscribeMapping("/download-speed")
    fun downloadSpeed(): DownloadSpeed {
        return DownloadSpeed(0L)
    }

    @SubscribeMapping("/vg-username")
    fun vgUsername(): String {
        return runBlocking { appEndpointService.loggedInUser() }
    }

    @SubscribeMapping("/error-count")
    fun errorCount(): ErrorCount {
        return ErrorCount(0)
    }

    @SubscribeMapping("/posts/new")
    fun posts(): List<Post> {
        return runBlocking { appEndpointService.findAllPosts() }
    }

    @SubscribeMapping("/images/{postId}")
    fun postsDetails(@DestinationVariable("postId") postId: Long): List<Image> {
        return runBlocking { appEndpointService.findImagesByPostEntityId(postId) }
    }

    @SubscribeMapping("/threads")
    fun queued(): List<Thread> {
        return runBlocking { appEndpointService.findAllThreads() }
    }
}