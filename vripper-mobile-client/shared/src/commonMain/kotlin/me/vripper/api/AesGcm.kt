package me.vripper.api

/**
 * End-to-end AES-256-GCM encryption used by the VRipper gRPC transport.
 *
 * Payload format matches the server: 12-byte IV followed by the GCM cipher text and
 * 16-byte authentication tag. The key is the SHA-256 digest of the pass phrase.
 */
interface AesGcm {
    fun encrypt(data: ByteArray, passPhrase: String): ByteArray
    fun decrypt(data: ByteArray, passPhrase: String): ByteArray
}

expect fun createAesGcm(): AesGcm
