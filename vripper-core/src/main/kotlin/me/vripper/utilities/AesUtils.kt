package me.vripper.utilities

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.withLock
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
object AesUtils {

    val mutex = ReentrantLock()

    val secureRandom: SecureRandom = SecureRandom()
    val cipher: Cipher = Cipher.getInstance(AES_ALGORITHM_GCM)

    const val SHA_CRYPT: String = "SHA-256"
    const val AES_ALGORITHM: String = "AES"
    const val AES_ALGORITHM_GCM: String = "AES/GCM/NoPadding"
    const val IV_LENGTH_ENCRYPT: Int = 12
    const val TAG_LENGTH_ENCRYPT: Int = 16

    fun aesDecrypt(data: ByteArray, passPhrase: String): ByteArray {

        val key = generateAesKeyFromPassphrase(passPhrase)

        val iv = ByteArray(IV_LENGTH_ENCRYPT)
        System.arraycopy(data, 0, iv, 0, iv.size)
        val encryptedText = ByteArray(data.size - IV_LENGTH_ENCRYPT)
        System.arraycopy(data, IV_LENGTH_ENCRYPT, encryptedText, 0, encryptedText.size)

        val gcmSpec = GCMParameterSpec(TAG_LENGTH_ENCRYPT * 8, iv)
        val decryptedBytes = mutex.withLock {
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            cipher.doFinal(encryptedText)
        }

        return decryptedBytes
    }

    fun aesEncrypt(data: ByteArray, passPhrase: String): ByteArray {

        val iv = ByteArray(IV_LENGTH_ENCRYPT)
        secureRandom.nextBytes(iv)

        val key = generateAesKeyFromPassphrase(passPhrase)

        val gcmSpec = GCMParameterSpec(TAG_LENGTH_ENCRYPT * 8, iv)
        val encryptedBytes = mutex.withLock {
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            cipher.doFinal(data)
        }

        // Combine IV and encrypted text and encode them as Base64
        val combinedIvAndCipherText = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combinedIvAndCipherText, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combinedIvAndCipherText, iv.size, encryptedBytes.size)

        return combinedIvAndCipherText
    }

    fun generateAesKeyFromPassphrase(passPhrase: String): SecretKeySpec {
        val sha256 = MessageDigest.getInstance(SHA_CRYPT)
        val keyBytes = sha256.digest(passPhrase.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }
}