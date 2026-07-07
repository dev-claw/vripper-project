package me.vripper.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.vripper.api.EndpointApi
import me.vripper.api.createEndpointApi

sealed interface SessionState {
    val config: SessionConfig

    data class Disconnected(
        override val config: SessionConfig,
        val error: String? = null,
    ) : SessionState

    data class Connecting(
        override val config: SessionConfig,
    ) : SessionState

    data class Connected(
        override val config: SessionConfig,
        val api: EndpointApi,
        val version: String,
        val user: String,
    ) : SessionState
}

class SessionManager(
    private val store: SessionStore = SessionStore(),
    private val apiFactory: (SessionConfig) -> EndpointApi = { config ->
        createEndpointApi(config.host, config.port, config.passPhrase)
    },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectJob: Job? = null
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected(store.load()))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun connect(config: SessionConfig) {
        if (_state.value is SessionState.Connecting) return
        connectJob?.cancel()
        (_state.value as? SessionState.Connected)?.api?.close()
        _state.value = SessionState.Connecting(config)
        connectJob = scope.launch { connectInternal(config) }
    }

    private suspend fun connectInternal(config: SessionConfig) {
        var api: EndpointApi? = null
        try {
            api = apiFactory(config)
            val version = api.getVersion()
            val user = api.loggedInUser()
            store.save(config)
            _state.value = SessionState.Connected(config, api, version, user)
        } catch (t: Throwable) {
            runCatching { api?.close() }
            if (t is CancellationException) throw t
            _state.value = SessionState.Disconnected(
                config = config,
                error = t.connectionMessage(config),
            )
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        val current = _state.value
        (current as? SessionState.Connected)?.api?.close()
        _state.value = SessionState.Disconnected(current.config)
    }
}

private fun Throwable.connectionMessage(config: SessionConfig): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("AEADBadTag", ignoreCase = true) ||
            raw.contains("BadPadding", ignoreCase = true) ||
            raw.contains("tag mismatch", ignoreCase = true) -> "Wrong pass phrase"

        raw.contains("UNAVAILABLE", ignoreCase = true) ||
            raw.contains("ConnectException", ignoreCase = true) ||
            raw.contains("UnknownHost", ignoreCase = true) -> "Cannot reach ${config.displayName}"

        raw.isBlank() -> "Unable to connect to ${config.displayName}"
        else -> raw
    }
}
