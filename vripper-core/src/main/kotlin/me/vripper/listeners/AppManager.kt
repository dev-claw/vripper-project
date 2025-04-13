package me.vripper.listeners

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.event.EventBus
import me.vripper.event.SettingsUpdateEvent
import me.vripper.services.*
import me.vripper.utilities.DatabaseManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AppManager : KoinComponent {
    private val eventBus: EventBus by inject()
    private val dataTransaction: DataTransaction by inject()
    private val metadataService: MetadataService by inject()
    private val settingsService: SettingsService by inject()
    private val vgAuthService: VGAuthService by inject()
    private val downloadSpeedService: DownloadSpeedService by inject()
    private val httpService: HTTPService by inject()
    private val retryPolicyService: RetryPolicyService by inject()
    private val threadCacheService: ThreadCacheService by inject()
    private val downloadService: DownloadService by inject()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

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
            }
        }
        DatabaseManager.connect()
        dataTransaction.setDownloadingToStopped()
        dataTransaction.stopImagesByPostIdAndIsNotCompleted()
        settingsService.init()
        metadataService.fetchExisting()
        downloadSpeedService.init()
        downloadService.init()
    }

    fun stop() {
        job?.cancel()
        if (DatabaseManager.isConnected()) {
            downloadService.halt()
            downloadService.stop()
            downloadSpeedService.halt()
            DatabaseManager.disconnect()
        }
    }
}
