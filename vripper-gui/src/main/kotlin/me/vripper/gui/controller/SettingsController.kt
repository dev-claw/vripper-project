package me.vripper.gui.controller

import me.vripper.gui.model.settings.ConnectionSettingsModel
import me.vripper.gui.model.settings.DownloadSettingsModel
import me.vripper.gui.model.settings.SystemSettingsModel
import me.vripper.gui.model.settings.ViperSettingsModel
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.model.*
import tornadofx.Controller

class SettingsController : Controller() {

    suspend fun findDownloadSettings(): DownloadSettings {
        return runCatching {
            AppEndpointManager.currentAppEndpointService().getSettings().downloadSettings
        }.getOrDefault(
            DownloadSettings()
        )
    }

    suspend fun findConnectionSettings(): ConnectionSettings {
        return runCatching {
            AppEndpointManager.currentAppEndpointService().getSettings().connectionSettings
        }.getOrDefault(ConnectionSettings())
    }

    suspend fun findViperGirlsSettings(): ViperSettings {
        return runCatching { AppEndpointManager.currentAppEndpointService().getSettings().viperSettings }.getOrDefault(
            ViperSettings()
        )
    }

    suspend fun findSystemSettings(): SystemSettings {
        return runCatching { AppEndpointManager.currentAppEndpointService().getSettings().systemSettings }.getOrDefault(
            SystemSettings()
        )
    }

    suspend fun saveNewSettings(
        downloadSettingsModel: DownloadSettingsModel,
        connectionSettingsModel: ConnectionSettingsModel,
        viperSettingsModel: ViperSettingsModel,
        systemSettingsModel: SystemSettingsModel
    ) {
        runCatching {
            AppEndpointManager.currentAppEndpointService().saveSettings(
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
    }

    suspend fun getProxies(): List<String> {
        return runCatching { AppEndpointManager.currentAppEndpointService().getProxies() }.getOrDefault(emptyList())
    }
}