package me.vripper.listeners

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.event.EventBus
import me.vripper.event.PostCompletedEvent
import me.vripper.event.SettingsUpdateEvent
import me.vripper.services.*
import me.vripper.services.download.DownloadService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AppManager : KoinComponent {
    private val eventBus: EventBus by inject()
    private val dataAccessService: DataAccessService by inject()
    private val settingsService: SettingsService by inject()
    private val vgAuthService: VGAuthService by inject()
    private val downloadSpeedService: DownloadSpeedService by inject()
    private val httpService: HTTPService by inject()
    private val retryPolicyService: RetryPolicyService by inject()
    private val threadCacheService: ThreadCacheService by inject()
    private val downloadService: DownloadService by inject()
    private val postActionService: PostActionService by inject()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var postActionJob: Job? = null

    fun start() {
        job?.cancel()
        job = coroutineScope.launch {
            eventBus.events.filterIsInstance(SettingsUpdateEvent::class).collect {
                httpService.client.close()
                httpService.buildRequestConfig(it.settings.connectionSettings.timeout.toLong())
                httpService.buildConnectionConfig(it.settings.connectionSettings.timeout.toLong())
                httpService.buildConnectionPool()
                httpService.buildClientBuilder()
                vgAuthService.authenticate(it.settings)
                retryPolicyService.maxAttempts = it.settings.connectionSettings.maxAttempts
                threadCacheService.invalidate()
                postActionService.recover()
            }
        }
        postActionJob?.cancel()
        postActionJob = coroutineScope.launch {
            eventBus.events.filterIsInstance(PostCompletedEvent::class).collect {
                postActionService.onPostCompleted(it.postEntityId)
            }
        }
        dataAccessService.setDownloadingToStopped()
        dataAccessService.stopImagesByPostEntityIdAndIsNotCompleted()
        settingsService.init()
        postActionService.recover()
        downloadSpeedService.init()
        downloadService.init()
    }

    fun stop() {
        job?.cancel()
        postActionJob?.cancel()
        downloadService.halt()
        downloadService.stop()
        downloadSpeedService.halt()
    }
}
