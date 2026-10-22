package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.model.LogEntry

interface ILogEndpoint {
    fun onNewLog(): Flow<LogEntry>
    suspend fun initLogger()
}
