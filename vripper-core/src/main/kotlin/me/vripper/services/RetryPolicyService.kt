package me.vripper.services

import dev.failsafe.RetryPolicy
import me.vripper.utilities.LoggerDelegate
import java.time.temporal.ChronoUnit

internal class RetryPolicyService {
    private val log by LoggerDelegate()
    var maxAttempts: Int = 3

    fun <T> buildRetryPolicy(message: String): RetryPolicy<T> {
        return RetryPolicy.builder<T>().withDelay(2, 5, ChronoUnit.SECONDS).withMaxAttempts(maxAttempts)
            .onFailedAttempt {
                log.warn(message + "#${it.attemptCount} tries failed", it.lastException)
            }.build()
    }
}