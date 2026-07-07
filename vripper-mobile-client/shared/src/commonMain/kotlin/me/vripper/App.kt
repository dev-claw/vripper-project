package me.vripper

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.vripper.session.SessionState
import me.vripper.ui.MainScreen
import me.vripper.ui.SessionScreen
import me.vripper.ui.VripperTheme

@Composable
fun App() {
    VripperTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val manager = AppContainer.sessionManager
            val state by manager.state.collectAsState()

            when (val current = state) {
                is SessionState.Connected -> {
                    MainScreen(session = current, onDisconnect = manager::disconnect)
                }

                else -> {
                    SessionScreen(state = current, onConnect = manager::connect)
                }
            }
        }
    }
}
