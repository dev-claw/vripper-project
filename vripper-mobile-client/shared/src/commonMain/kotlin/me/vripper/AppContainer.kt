package me.vripper

import me.vripper.session.SessionManager

object AppContainer {
    val sessionManager: SessionManager by lazy { SessionManager() }
}
