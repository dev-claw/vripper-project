package me.vripper.preferences

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Client-side preferences that are not stored on the server.
 *
 * Keeping these separate from [me.vripper.data.Settings] makes it clear that the
 * appearance choice belongs to the device, not to the VRipper installation.
 */
class AppPreferences(private val settings: Settings = Settings()) {
    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        settings.putString(THEME_MODE_KEY, mode.name)
        _themeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode =
        settings.getStringOrNull(THEME_MODE_KEY)
            ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
            ?: ThemeMode.SYSTEM

    private companion object {
        const val THEME_MODE_KEY = "appearance.themeMode"
    }
}
