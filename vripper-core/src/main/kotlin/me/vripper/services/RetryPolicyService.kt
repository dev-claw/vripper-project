package me.vripper.services

import dev.failsafe.RetryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import me.vripper.event.EventBus
import me.vripper.event.SettingsUpdateEvent
import me.vripper.utilities.LoggerDelegate
import java.time.temporal.ChronoUnit

internal class RetryPolicyService(
    val eventBus: EventBus, settingsService: SettingsService
) {
    private val log by LoggerDelegate()
    private var maxAttempts: Int = settingsService.settings.connectionSettings.maxAttempts
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init() {
        coroutineScope.launch {
            eventBus.events.filterIsInstance(SettingsUpdateEvent::class).collect {
                if (maxAttempts != it.settings.connectionSettings.maxAttempts) {
                    maxAttempts = it.settings.connectionSettings.maxAttempts
                }
            }
        }
    }

    fun <T> buildRetryPolicy(message: String): RetryPolicy<T> {
        return RetryPolicy.builder<T>().withDelay(2, 5, ChronoUnit.SECONDS).withMaxAttempts(maxAttempts)
            .onFailedAttempt {
                log.warn(message + "#${it.attemptCount} tries failed", it.lastException)
            }.build()
    }
}