package me.vripper.api

@Suppress("UNUSED_PARAMETER")
actual fun createAesGcm(): AesGcm =
    throw UnsupportedOperationException("Encrypted transport is not implemented for iOS yet")

@Suppress("UNUSED_PARAMETER")
actual fun createEndpointApi(host: String, port: Int, passPhrase: String): EndpointApi =
    throw UnsupportedOperationException("The VRipper client is not implemented for iOS yet")
