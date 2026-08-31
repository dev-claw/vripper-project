package me.vripper.gui.controller

import me.vripper.gui.model.settings.*
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

    suspend fun findHostSettings(): Map<HostName, Map<HostSettingKey, String>> {
        return runCatching { AppEndpointManager.currentAppEndpointService().getSettings().hostSettings }.getOrDefault(
            emptyMap()
        )
    }

    suspend fun findAutomationSettings(): AutomationSettings {
        return runCatching {
            AppEndpointManager.currentAppEndpointService().getSettings().automationSettings
        }.getOrDefault(
            AutomationSettings()
        )
    }

    suspend fun saveNewSettings(
        downloadSettingsModel: DownloadSettingsModel,
        connectionSettingsModel: ConnectionSettingsModel,
        viperSettingsModel: ViperSettingsModel,
        systemSettingsModel: SystemSettingsModel,
        hostSettingsModel: List<HostSettingsModel>,
        automationSettingsModel: AutomationSettingsModel,
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
                        ),
                    hostSettings = run {
                        val hostSettingsMap = mutableMapOf<HostName, MutableMap<HostSettingKey, String>>()
                        hostSettingsModel.forEach { model ->
                            val hostName = model.host
                            val settingKey = model.settingKey
                            hostSettingsMap.computeIfAbsent(hostName) { mutableMapOf() }[settingKey] =
                                model.valueProperty.value.toString()
                        }
                        hostSettingsMap
                    },
                    automationSettings = AutomationSettings(
                        automationSettingsModel.compress,
                        automationSettingsModel.trigger,
                        automationSettingsModel.collect,
                        TriggerAction.valueOf(automationSettingsModel.triggerAction),
                        automationSettingsModel.moveDestination,
                        automationSettingsModel.moveOverride,
                        automationSettingsModel.webhookUrl,
                        WebhookMethod.valueOf(automationSettingsModel.webhookMethod),
                        automationSettingsModel.webhookPayload,
                        automationSettingsModel.scriptPath,
                        automationSettingsModel.scriptArguments,
                    )
                )
            )
        }
    }

    suspend fun getProxies(): List<String> {
        return runCatching { AppEndpointManager.currentAppEndpointService().getProxies() }.getOrDefault(emptyList())
    }
}