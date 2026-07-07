package me.vripper.gui.services

import me.vripper.services.IAppEndpointService

interface IGrpcEndpointService : IAppEndpointService {
    fun connect(host: String, port: Int, passPhrase: String)
    fun disconnect()
    suspend fun versionCheck(): Boolean?
}
