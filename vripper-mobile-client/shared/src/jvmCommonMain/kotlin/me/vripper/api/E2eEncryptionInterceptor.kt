package me.vripper.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer

/**
 * Applies the VRipper end-to-end encryption to every gRPC frame.
 *
 * gRPC messages are wrapped in frames made of a one byte flag, a four byte big-endian
 * length and the payload. Only data frames (flag 0) are encrypted, leaving metadata and
 * compression flags untouched. This is the transport level equivalent of the
 * `ClientE2eEncryptingInterceptor` used by the desktop client.
 */
internal class E2eEncryptionInterceptor(
    private val passPhrase: String,
    private val crypto: AesGcm,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body
        val requestContentType = request.header("Content-Type") ?: requestBody?.contentType()?.toString()
        if (requestBody == null || !isGrpc(requestContentType)) {
            return chain.proceed(request)
        }

        val encryptedRequest = request.newBuilder()
            .method(request.method, EncryptingRequestBody(requestBody, passPhrase, crypto))
            .header("grpc-accept-encoding", "identity")
            .build()

        // Server-streaming calls stay open while idle; the default 10s socket read timeout
        // would otherwise abort them. Wire models these as duplex request bodies.
        // Grabbing a thread can hit the remote forum and may take longer than the default.
        val proceedChain = when {
            requestBody.isDuplex() -> chain.withReadTimeout(0, TimeUnit.MILLISECONDS)
            request.url.encodedPath.endsWith("/grab") ->
                chain.withReadTimeout(GRAB_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            else -> chain
        }
        val response = proceedChain.proceed(encryptedRequest)
        val responseContentType = response.header("Content-Type") ?: response.body.contentType()?.toString()
        if (!isGrpc(responseContentType)) return response

        return response.newBuilder()
            .body(DecryptingResponseBody(response.body, passPhrase, crypto))
            .build()
    }

    private fun isGrpc(contentType: String?): Boolean =
        contentType?.startsWith("application/grpc", ignoreCase = true) == true

    private companion object {
        const val GRAB_READ_TIMEOUT_SECONDS = 120
    }
}

private class EncryptingRequestBody(
    private val delegate: RequestBody,
    private val passPhrase: String,
    private val crypto: AesGcm,
) : RequestBody() {
    override fun contentType() = delegate.contentType()

    override fun contentLength() = -1L

    override fun isDuplex() = delegate.isDuplex()

    override fun isOneShot() = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val buffered = FrameEncryptingSink(sink, passPhrase, crypto).buffer()
        delegate.writeTo(buffered)
        if (buffered.isOpen) buffered.flush()
    }
}

/**
 * Encrypts gRPC frames as they are written, so duplex/streaming request bodies work
 * without buffering the whole request. A full frame payload is buffered because
 * AES-GCM is applied per message.
 */
internal class FrameEncryptingSink(
    private val downstream: Sink,
    private val passPhrase: String,
    private val crypto: AesGcm,
) : Sink {
    private val header = Buffer()
    private val payload = Buffer()
    private var flag = 0
    private var remaining = -1L

    override fun write(source: Buffer, byteCount: Long) {
        source.require(byteCount)
        var left = byteCount
        while (left > 0L) {
            if (remaining < 0L) {
                val take = minOf(HEADER_SIZE - header.size, left)
                header.write(source, take)
                left -= take
                if (header.size == HEADER_SIZE) {
                    flag = header.readByte().toInt() and 0xff
                    val length = header.readInt()
                    if (length < 0) throw IOException("Invalid gRPC frame length: $length")
                    payload.clear()
                    remaining = length.toLong()
                    if (remaining == 0L) emitFrame()
                }
            } else {
                val take = minOf(remaining, left)
                payload.write(source, take)
                left -= take
                remaining -= take
                if (remaining == 0L) emitFrame()
            }
        }
    }

    private fun emitFrame() {
        val raw = payload.readByteArray()
        val data = if (flag == 0) crypto.encrypt(raw, passPhrase) else raw
        val frame = Buffer()
        frame.writeByte(flag)
        frame.writeInt(data.size)
        frame.write(data)
        downstream.write(frame, frame.size)
        header.clear()
        remaining = -1L
    }

    override fun flush() = downstream.flush()

    override fun timeout(): Timeout = downstream.timeout()

    override fun close() = downstream.close()

    private companion object {
        const val HEADER_SIZE = 5L
    }
}

private class DecryptingResponseBody(
    private val delegate: ResponseBody,
    private val passPhrase: String,
    private val crypto: AesGcm,
) : ResponseBody() {
    override fun contentType() = delegate.contentType()

    override fun contentLength() = -1L

    override fun source(): BufferedSource =
        DecryptingSource(delegate.source(), passPhrase, crypto).buffer()
}

internal class DecryptingSource(
    private val upstream: BufferedSource,
    private val passPhrase: String,
    private val crypto: AesGcm,
) : Source {
    private val output = Buffer()

    override fun read(sink: Buffer, byteCount: Long): Long {
        while (output.size == 0L) {
            if (!readFrame()) return -1L
        }
        return output.read(sink, minOf(byteCount, output.size))
    }

    private fun readFrame(): Boolean {
        if (upstream.exhausted()) return false
        val flag = upstream.readByte().toInt() and 0xff
        val length = upstream.readInt()
        if (length < 0) throw IOException("Invalid gRPC frame length: $length")
        val payload = upstream.readByteArray(length.toLong())

        if (flag == 0) {
            val plain = crypto.decrypt(payload, passPhrase)
            output.writeByte(0)
            output.writeInt(plain.size)
            output.write(plain)
        } else {
            output.writeByte(flag)
            output.writeInt(length)
            output.write(payload)
        }
        return true
    }

    override fun timeout(): Timeout = upstream.timeout()

    override fun close() = upstream.close()
}
