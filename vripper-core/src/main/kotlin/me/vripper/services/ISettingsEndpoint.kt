package me.vripper.services

import kotlinx.coroutines.flow.Flow
import me.vripper.model.Settings

interface ISettingsEndpoint {
    fun onVGUserUpdate(): Flow<String>
    suspend fun getSettings(): Settings
    suspend fun saveSettings(settings: Settings)
    suspend fun getProxies(): List<String>
    fun onUpdateSettings(): Flow<Settings>
    suspend fun loggedInUser(): String
    suspend fun getVersion(): String
}
