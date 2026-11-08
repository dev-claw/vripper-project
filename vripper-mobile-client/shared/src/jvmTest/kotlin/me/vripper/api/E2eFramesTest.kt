package me.vripper.api

import okio.Buffer
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class E2eFramesTest {
    private val crypto = object : AesGcm {
        override fun encrypt(data: ByteArray, passPhrase: String): ByteArray = xor(data)
        override fun decrypt(data: ByteArray, passPhrase: String): ByteArray = xor(data)
    }

    @Test
    fun `request frames encrypt only data frames`() {
        val header = byteArrayOf(1, 2, 3)
        val body = byteArrayOf(4, 5, 6, 7)

        val output = encrypt(frame(flag = 0, header + body))

        val decoded = decodeFrames(output)
        assertEquals(1, decoded.size)
        assertEquals(0, decoded.single().flag)
        assertContentEquals(xor(header + body), decoded.single().payload)
    }

    @Test
    fun `request frames leave compressed frames untouched`() {
        val payload = byteArrayOf(9, 8, 7)

        val output = encrypt(frame(flag = 1, payload))

        val decoded = decodeFrames(output).single()
        assertEquals(1, decoded.flag)
        assertContentEquals(payload, decoded.payload)
    }

    @Test
    fun `request frames are reassembled when split across writes`() {
        val first = byteArrayOf(1, 1, 1)
        val second = byteArrayOf(2, 2)
        val input = frame(flag = 0, first) + frame(flag = 0, second)

        val downstream = Buffer()
        val sink = FrameEncryptingSink(downstream, "secret", crypto)
        val source = Buffer().write(input)
        while (!source.exhausted()) {
            sink.write(Buffer().write(source.readByteArray(1)), 1)
        }
        sink.flush()

        val decoded = decodeFrames(downstream.readByteArray())
        assertEquals(2, decoded.size)
        assertContentEquals(xor(first), decoded[0].payload)
        assertContentEquals(xor(second), decoded[1].payload)
    }

    @Test
    fun `response source decrypts a stream of frames`() {
        val first = byteArrayOf(1, 1, 1)
        val second = byteArrayOf(2, 2)
        val stream = frame(flag = 0, xor(first)) + frame(flag = 0, xor(second))

        val source = DecryptingSource(Buffer().write(stream), "secret", crypto)
        val result = Buffer()
        source.buffer().readAll(result)

        val decoded = decodeFrames(result.readByteArray())
        assertEquals(2, decoded.size)
        assertContentEquals(first, decoded[0].payload)
        assertContentEquals(second, decoded[1].payload)
    }

    private fun encrypt(input: ByteArray): ByteArray {
        val downstream = Buffer()
        val sink = FrameEncryptingSink(downstream, "secret", crypto)
        sink.write(Buffer().write(input), input.size.toLong())
        sink.flush()
        return downstream.readByteArray()
    }

    private fun xor(data: ByteArray) = ByteArray(data.size) { index -> (data[index].toInt() xor 0x5A).toByte() }

    private fun frame(flag: Int, payload: ByteArray): ByteArray {
        val buffer = Buffer()
        buffer.writeByte(flag)
        buffer.writeInt(payload.size)
        buffer.write(payload)
        return buffer.readByteArray()
    }

    private data class Frame(val flag: Int, val payload: ByteArray)

    private fun decodeFrames(bytes: ByteArray): List<Frame> {
        val source = Buffer().write(bytes)
        val frames = mutableListOf<Frame>()
        while (!source.exhausted()) {
            val flag = source.readByte().toInt() and 0xff
            val length = source.readInt()
            frames += Frame(flag, source.readByteArray(length.toLong()))
        }
        return frames
    }
}
