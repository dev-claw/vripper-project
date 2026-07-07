package me.vripper.session

import com.russhwolf.settings.Settings

data class SessionConfig(
    val host: String = "127.0.0.1",
    val port: Int = 30000,
    val passPhrase: String = "",
) {
    val displayName: String
        get() = "$host:$port"
}

/**
 * Persists the last used session so the user does not have to re-enter it.
 * Mirrors the desktop client default of remembering host, port and pass phrase.
 */
class SessionStore(private val settings: Settings = Settings()) {
    fun load(): SessionConfig = SessionConfig(
        host = settings.getString(HOST_KEY, DEFAULT_HOST),
        port = settings.getInt(PORT_KEY, DEFAULT_PORT),
        passPhrase = settings.getString(PASS_PHRASE_KEY, ""),
    )

    fun save(config: SessionConfig) {
        settings.putString(HOST_KEY, config.host)
        settings.putInt(PORT_KEY, config.port)
        settings.putString(PASS_PHRASE_KEY, config.passPhrase)
    }

    private companion object {
        const val HOST_KEY = "session.host"
        const val PORT_KEY = "session.port"
        const val PASS_PHRASE_KEY = "session.passphrase"
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 30000
    }
}
