package me.vripper.utilities

import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.TokensInheritanceStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import me.vripper.event.EventBus
import me.vripper.event.SettingsUpdateEvent
import me.vripper.services.SettingsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration

internal object RequestLimit : KoinComponent {
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsService: SettingsService by inject()
    private val eventBus: EventBus by inject()
    private val bucket: Bucket = Bucket.builder().addLimit {
        it.capacity(settingsService.settings.viperSettings.requestLimit)
            .refillGreedy(settingsService.settings.viperSettings.requestLimit, Duration.ofSeconds(1))
    }.build()

    init {
        coroutineScope.launch {
            eventBus.events.filterIsInstance(SettingsUpdateEvent::class).collectLatest { event ->
                bucket.replaceConfiguration(BucketConfiguration.builder().addLimit {
                    it.capacity(event.settings.viperSettings.requestLimit)
                        .refillGreedy(event.settings.viperSettings.requestLimit, Duration.ofSeconds(1))
                }.build(), TokensInheritanceStrategy.RESET)
            }
        }
    }

    fun getPermit(tokens: Long) {
        bucket.asBlocking().consume(tokens)
    }
}
