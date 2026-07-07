package me.vripper.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.vripper.api.EndpointApi

class SessionStatsController(
    api: EndpointApi,
    scope: CoroutineScope,
    initialUser: String,
) {
    private val _user = MutableStateFlow(initialUser)
    val user: StateFlow<String> = _user.asStateFlow()

    private val _downloadSpeed = MutableStateFlow(0L)
    val downloadSpeed: StateFlow<Long> = _downloadSpeed.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    private val _tasksRunning = MutableStateFlow(false)
    val tasksRunning: StateFlow<Boolean> = _tasksRunning.asStateFlow()

    init {
        scope.observeStream(api::onVGUserUpdate, onValue = { _user.value = it })
        scope.observeStream(api::onDownloadSpeed, onValue = { _downloadSpeed.value = it })
        scope.observeStream(api::onErrorCountUpdate, onValue = { _errorCount.value = it })
        scope.observeStream(api::onTasksRunning, onValue = { _tasksRunning.value = it })
    }
}
