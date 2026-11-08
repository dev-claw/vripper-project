package me.vripper.api

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class JvmAesGcm : AesGcm {
    private val secureRandom = SecureRandom()

    override fun encrypt(data: ByteArray, passPhrase: String): ByteArray {
        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(passPhrase), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return iv + cipher.doFinal(data)
    }

    override fun decrypt(data: ByteArray, passPhrase: String): ByteArray {
        require(data.size > IV_LENGTH) { "Encrypted payload is too short" }
        val iv = data.copyOfRange(0, IV_LENGTH)
        val cipherText = data.copyOfRange(IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(passPhrase), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun key(passPhrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(passPhrase.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}

actual fun createAesGcm(): AesGcm = JvmAesGcm()
