package me.vripper.api

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AesGcmTest {
    @Test
    fun `encrypts with iv and gcm tag and decrypts back`() {
        val crypto = JvmAesGcm()
        val plain = "hello vRipper".encodeToByteArray()

        val encrypted = crypto.encrypt(plain, "correct horse battery staple")

        assertEquals(12 + plain.size + 16, encrypted.size)
        assertContentEquals(plain, crypto.decrypt(encrypted, "correct horse battery staple"))
    }

    @Test
    fun `uses a fresh iv for every message`() {
        val crypto = JvmAesGcm()
        val plain = byteArrayOf(1, 2, 3)

        val first = crypto.encrypt(plain, "secret")
        val second = crypto.encrypt(plain, "secret")

        assertEquals(false, first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12)))
    }
}
