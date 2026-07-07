package me.vripper.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.vripper.api.EndpointApi
import me.vripper.data.Settings

class SettingsController(
    private val api: EndpointApi,
    private val scope: CoroutineScope,
) {
    private val _settings = MutableStateFlow<Settings?>(null)
    val settings: StateFlow<Settings?> = _settings.asStateFlow()

    private val _proxies = MutableStateFlow<List<String>>(emptyList())
    val proxies: StateFlow<List<String>> = _proxies.asStateFlow()

    var isLoading by mutableStateOf(true)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var savedMessage by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            load()
            observeUpdates()
        }
    }

    fun refresh() {
        scope.launch { load() }
    }

    fun save(settings: Settings) {
        scope.launch {
            isSaving = true
            try {
                api.saveSettings(settings)
                _settings.value = settings
                error = null
                savedMessage = "Settings saved"
                delay(2_500)
                savedMessage = null
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = t.userMessage("Unable to save settings")
            } finally {
                isSaving = false
            }
        }
    }

    private suspend fun load() {
        isLoading = true
        try {
            _settings.value = api.getSettings()
            _proxies.value = runCatching { api.getProxies() }.getOrDefault(emptyList())
            error = null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            error = t.userMessage("Unable to load settings")
        } finally {
            isLoading = false
        }
    }

    private fun observeUpdates() {
        scope.observeStream(
            api::onUpdateSettings,
            onValue = { updated ->
                if (!isSaving) {
                    _settings.value = updated
                }
            },
            onError = { /* The settings screen is refreshed manually. */ },
        )
    }
}
