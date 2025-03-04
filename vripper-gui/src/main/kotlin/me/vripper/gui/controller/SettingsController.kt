package me.vripper.gui.controller

import me.vripper.gui.model.settings.ConnectionSettingsModel
import me.vripper.gui.model.settings.DownloadSettingsModel
import me.vripper.gui.model.settings.SystemSettingsModel
import me.vripper.gui.model.settings.ViperSettingsModel
import me.vripper.model.*
import me.vripper.services.IAppEndpointService
import tornadofx.Controller

class SettingsController : Controller() {

    private val widgetsController: WidgetsController by inject()
    private val appEndpointService: IAppEndpointService by di(if (widgetsController.currentSettings.localSession) "localAppEndpointService" else "remoteAppEndpointService")

    suspend fun findDownloadSettings(): DownloadSettings {
        return appEndpointService.getSettings().downloadSettings
    }

    suspend fun findConnectionSettings(): ConnectionSettings {
        return appEndpointService.getSettings().connectionSettings
    }

    suspend fun findViperGirlsSettings(): ViperSettings {
        return appEndpointService.getSettings().viperSettings
    }

    suspend fun findSystemSettings(): SystemSettings {
        return appEndpointService.getSettings().systemSettings
    }

    suspend fun saveNewSettings(
        downloadSettingsModel: DownloadSettingsModel,
        connectionSettingsModel: ConnectionSettingsModel,
        viperSettingsModel: ViperSettingsModel,
        systemSettingsModel: SystemSettingsModel
    ) {
        appEndpointService.saveSettings(
            Settings(
                downloadSettings = DownloadSettings(
                    downloadSettingsModel.downloadPath,
                    downloadSettingsModel.autoStart,
                    downloadSettingsModel.autoQueueThreshold,
                    downloadSettingsModel.forceOrder,
                    downloadSettingsModel.forumSubfolder,
                    downloadSettingsModel.threadSubLocation,
                    downloadSettingsModel.clearCompleted,
                    downloadSettingsModel.appendPostId
                ),
                connectionSettings = ConnectionSettings(
                    connectionSettingsModel.maxThreads,
                    connectionSettingsModel.maxTotalThreads,
                    connectionSettingsModel.timeout,
                    connectionSettingsModel.maxAttempts,
                ),
                viperSettings = ViperSettings(
                    viperSettingsModel.login,
                    viperSettingsModel.username,
                    viperSettingsModel.password,
                    viperSettingsModel.thanks,
                    viperSettingsModel.host,
                    viperSettingsModel.requestLimit,
                    viperSettingsModel.fetchMetadata,
                ),
                systemSettings =
                    SystemSettings(
                        systemSettingsModel.tempPath,
                        systemSettingsModel.enable,
                        systemSettingsModel.pollingRate,
                        systemSettingsModel.logEntries
                    )
            )
        )
    }

    suspend fun getProxies(): List<String> {
        return appEndpointService.getProxies()
    }
}