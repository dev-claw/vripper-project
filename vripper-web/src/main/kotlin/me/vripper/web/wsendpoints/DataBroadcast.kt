package me.vripper.web.wsendpoints

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import me.vripper.services.IAppEndpointService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class DataBroadcast(
    private val template: SimpMessagingTemplate
) : KoinComponent {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))

    @OptIn(FlowPreview::class)
    @PostConstruct
    private fun run() {
        runBlocking {
            appEndpointService.initLogger()
        }
        coroutineScope.launch {
            appEndpointService.onNewPosts().collect {
                template.convertAndSend("/topic/posts/new", listOf(it))
            }
        }

        coroutineScope.launch {
            appEndpointService.onUpdatePosts().collect {
                template.convertAndSend("/topic/posts/updated", listOf(it))
            }
        }

        coroutineScope.launch {
            appEndpointService.onDeletePosts().collect {
                template.convertAndSend("/topic/posts/deleted", listOf(it))
            }
        }

        coroutineScope.launch {
            appEndpointService.onQueueStateUpdate().collect {
                template.convertAndSend("/topic/queue-state", it)
            }
        }

        coroutineScope.launch {
            appEndpointService.onDownloadSpeed().collect {
                template.convertAndSend("/topic/download-speed", it)
            }
        }

        coroutineScope.launch {
            appEndpointService.onVGUserUpdate().collect {
                template.convertAndSend("/topic/vg-username", it)
            }
        }

        coroutineScope.launch {
            appEndpointService.onErrorCountUpdate().collect {
                template.convertAndSend("/topic/error-count", it)
            }
        }

        coroutineScope.launch {
            appEndpointService.onTasksRunning().collect {
                template.convertAndSend("/topic/loading", it)
            }
        }

        coroutineScope.launch {
            appEndpointService.onUpdateImages().collect {
                template.convertAndSend("/topic/images/${it.postId}", listOf(it))
            }
        }

        coroutineScope.launch {
            appEndpointService.onNewThread()
                .collect {
                    template.convertAndSend("/topic/threads", listOf(it))
                }
        }

        coroutineScope.launch {
            appEndpointService.onDeleteThread()
                .collect {
                    template.convertAndSend("/topic/threads/deleted", it)
                }
        }

        coroutineScope.launch {
            appEndpointService.onClearThreads()
                .collect {
                    template.convertAndSend("/topic/threads/deletedAll", true)
                }
        }

        coroutineScope.launch {
            appEndpointService.onNewLog()
                .collect {
                    template.convertAndSend("/topic/logs/new", it)
                }
        }

        coroutineScope.launch {
            appEndpointService.onUpdateSettings()
                .collect {
                    template.convertAndSend("/topic/settings/update", it)
                }
        }

    }
}