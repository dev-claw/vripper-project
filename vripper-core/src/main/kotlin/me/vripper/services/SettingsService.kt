package me.vripper.services

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.vripper.event.EventBus
import me.vripper.event.SettingsUpdateEvent
import me.vripper.exception.ValidationException
import me.vripper.model.Settings
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import me.vripper.utilities.LoggerDelegate
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileWriter
import java.nio.file.*
import kotlin.io.path.readText

class SettingsService(private val eventBus: EventBus) {

    private val log by LoggerDelegate()
    private val configPath = VRIPPER_DIR.resolve("config.json")
    private val customProxiesPath = VRIPPER_DIR.resolve("proxies.json")
    private val proxies: MutableSet<String> = HashSet()

    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    var settings = Settings()

    init {
        init()
    }

    private fun init() {
        loadViperProxies()
        restore()
        eventBus.publishEvent(SettingsUpdateEvent(settings))
    }

    private fun loadViperProxies() {
        try {
            SettingsService::class.java.getResourceAsStream("/proxies.json")?.use {
                val defaultProxies: List<String> = json.decodeFromStream(it)
                val customProxies: List<String> = if (customProxiesPath.toFile()
                        .exists() && Files.isRegularFile(customProxiesPath)
                ) {
                    try {
                        json.decodeFromString(
                            customProxiesPath.readText()
                        )
                    } catch (e: Exception) {
                        log.error("Failed to read custom proxies", e)
                        emptyList()
                    }
                } else {
                    try {
                        FileWriter(customProxiesPath.toFile()).use { fw ->
                            fw.append("[]").append(System.lineSeparator())
                            fw.flush()
                        }
                    } catch (e: Exception) {
                        log.warn(
                            "Unable to create " + customProxiesPath.toFile().absolutePath, e
                        )
                    }
                    emptyList()
                }

                proxies.addAll(defaultProxies)
                proxies.addAll(customProxies)
            }
        } catch (e: Exception) {
            log.error("Failed to load vipergirls proxies list", e)
        }
        proxies.add("https://vipergirls.to")
    }

    fun getProxies(): List<String> {
        return ArrayList(proxies)
    }

    fun newSettings(settings: Settings) {
        check(settings)
        val viperSettings = if (settings.viperSettings.login) {
            if (this.settings.viperSettings.password != settings.viperSettings.password) {
                settings.viperSettings.copy(password = DigestUtils.md5Hex(settings.viperSettings.password))
            } else {
                settings.viperSettings
            }
        } else {
            settings.viperSettings.copy(username = "", password = "", thanks = false, login = false)
        }
        this.settings = settings.copy(viperSettings = viperSettings)
        save()
        eventBus.publishEvent(SettingsUpdateEvent(this@SettingsService.settings))
    }

    private fun restore() {
        try {
            if (configPath.toFile().exists()) {
                settings = json.decodeFromString(configPath.readText())
            }
        } catch (e: Exception) {
            log.error("Failed to restore user settings", e)
            settings = Settings()
        }
        if (!proxies.contains(settings.viperSettings.host)) {
            val viperSetting = settings.viperSettings.copy(host = "https://vipergirls.to")
            settings = settings.copy(viperSettings = viperSetting)
        }
        try {
            check(settings)
        } catch (e: ValidationException) {
            log.error("Your settings are invalid, using default settings", e)
            settings = Settings()
        }
        save()
    }

    private fun save() {
        try {
            Files.writeString(
                configPath,
                json.encodeToString(settings),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC
            )
        } catch (e: Exception) {
            log.error("Failed to store user settings", e)
        }
    }

    @Throws(ValidationException::class)
    fun check(settings: Settings) {
        val path: Path = try {
            Paths.get(settings.downloadSettings.downloadPath)
        } catch (e: InvalidPathException) {
            throw ValidationException(
                String.format(
                    "%s is invalid", settings.downloadSettings.downloadPath
                )
            )
        }
        if (!Files.exists(path)) {
            throw ValidationException(
                String.format(
                    "%s does not exist", settings.downloadSettings.downloadPath
                )
            )
        } else if (!Files.isDirectory(path)) {
            throw ValidationException(
                String.format(
                    "%s is not a directory", settings.downloadSettings.downloadPath
                )
            )
        }

        if (settings.downloadSettings.autoQueueThreshold < 0) {
            throw ValidationException("Invalid auto queue settings, value must be a positive integer")
        }

        if (settings.connectionSettings.maxGlobalConcurrent < 0 || settings.connectionSettings.maxGlobalConcurrent > 24) {
            throw ValidationException(
                "Invalid max global concurrent download settings, values must be in [0,24]"
            )
        }
        if (settings.connectionSettings.maxConcurrentPerHost < 1 || settings.connectionSettings.maxConcurrentPerHost > 4) {
            throw ValidationException("Invalid max concurrent download settings, values must be in [1,4]")
        }
        if (settings.connectionSettings.timeout < 1 || settings.connectionSettings.timeout > 300) {
            throw ValidationException(
                "Invalid connection timeout settings, values must be in [1,300]"
            )
        }
        if (settings.connectionSettings.maxAttempts < 1 || settings.connectionSettings.maxAttempts > 10) {
            throw ValidationException("Invalid maximum attempts settings, values must be in [1,10]")
        }
        if (settings.systemSettings.maxEventLog < 10 || settings.systemSettings.maxEventLog > 10000) {
            throw ValidationException(
                "Invalid maximum event log record settings, values must be in [100,10000]"
            )
        }
        if (settings.systemSettings.clipboardPollingRate < 500) {
            throw ValidationException(
                "Invalid clipboard monitoring polling rate settings, values must be >= 500"
            )
        }

        if (settings.viperSettings.requestLimit < 1 || settings.viperSettings.requestLimit > 5) {
            throw ValidationException(
                "Invalid request rate limit, values must be in [1,5]"
            )
        }
    }
}