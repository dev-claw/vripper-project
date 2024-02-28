package me.vripper.gui.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import me.vripper.services.SettingsService
import me.vripper.utilities.hash256
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.Path

object PreviewCache : KoinComponent {

    private val settingsService: SettingsService by inject()
    private val cachePath = Path(settingsService.settings.systemSettings.cachePath)

    init {
        Files.createDirectories(cachePath)
    }

    val cache: LoadingCache<String, ByteArray> = Caffeine
        .newBuilder()
        .weigher { _: String, value: ByteArray -> value.size }
        .maximumWeight(1024 * 1024 * 100)
        .build(PreviewCache::load)

    private fun load(url: String): ByteArray {
        val path = cachePath.resolve(url.hash256())
        if (Files.exists(path) && Files.isRegularFile(path)) {
            return Files.readAllBytes(path)
        }
        return URI.create(url).toURL().openStream().use { `is` ->
            val bytes = `is`.readAllBytes()
            Files.write(path, bytes)
            bytes
        }
    }
}