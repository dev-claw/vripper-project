package me.vripper.services

import me.vripper.model.ServerInfo

interface IEndpointLifecycle {
    suspend fun dbMigration(): String
    fun connectionState(): String
    suspend fun getServerInfo(): ServerInfo
}
