package me.vripper

import me.vripper.preferences.AppPreferences
import me.vripper.session.SessionManager

object AppContainer {
    val sessionManager: SessionManager by lazy { SessionManager() }
    val preferences: AppPreferences by lazy { AppPreferences() }
}
