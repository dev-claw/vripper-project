package me.vripper.listeners

import me.vripper.services.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

open class OnStartupListener : KoinComponent {
    private val dataTransaction: DataTransaction by inject()
    private val metadataService: MetadataService by inject()
    private val settingsService: SettingsService by inject()
    private val vgAuthService: VGAuthService by inject()
    private val downloadSpeedService: DownloadSpeedService by inject()
    private val httpService: HTTPService by inject()
    private val retryPolicyService: RetryPolicyService by inject()
    private val threadCacheService: ThreadCacheService by inject()
    private val downloadService: DownloadService by inject()

    open fun run() {
        dataTransaction.setDownloadingToStopped()
        dataTransaction.stopImagesByPostIdAndIsNotCompleted()
        vgAuthService.init()
        httpService.init()
        retryPolicyService.init()
        threadCacheService.init()
        settingsService.init()
        metadataService.init()
        downloadSpeedService.init()
        downloadService.init()
    }
}
