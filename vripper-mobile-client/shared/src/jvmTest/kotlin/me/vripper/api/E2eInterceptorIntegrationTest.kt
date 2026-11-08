package me.vripper.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class E2eInterceptorIntegrationTest {
    private val crypto = JvmAesGcm()
    private val passPhrase = "secret"
    private val grpcMediaType = "application/grpc".toMediaType()

    @Test
    fun `encrypts grpc frames even when content type is only set on the request body`() {
        val received = AtomicReference<ByteArray>()
        val responsePayload = byteArrayOf(10, 20, 30)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received.set(exchange.requestBody.readBytes())
            val response = frame(flag = 0, crypto.encrypt(responsePayload, passPhrase))
            exchange.responseHeaders.add("Content-Type", "application/grpc")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(E2eEncryptionInterceptor(passPhrase, crypto))
                .build()

            val requestBody = object : RequestBody() {
                override fun contentType() = grpcMediaType

                override fun writeTo(sink: BufferedSink) {
                    sink.write(frame(flag = 0, ByteArray(0)))
                }
            }
            val request = Request.Builder()
                .url("http://127.0.0.1:${server.address.port}/EndpointService/getVersion")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                val frames = decodeFrames(response.body.source().readByteArray())
                assertEquals(1, frames.size)
                assertEquals(0, frames.single().flag)
                assertContentEquals(responsePayload, frames.single().payload)
            }

            val requestFrames = decodeFrames(received.get())
            assertEquals(1, requestFrames.size)
            assertEquals(0, requestFrames.single().flag)
            assertEquals(12 + 16, requestFrames.single().payload.size)
            assertContentEquals(
                ByteArray(0),
                crypto.decrypt(requestFrames.single().payload, passPhrase),
            )
        } finally {
            server.stop(0)
        }
    }

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
