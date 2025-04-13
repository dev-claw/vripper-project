package me.vripper.web.grpc

import io.grpc.MethodDescriptor
import me.vripper.utilities.AesUtils
import java.io.ByteArrayInputStream
import java.io.InputStream


class ServerRequestDecryptor(private val passPhrase: String) : MethodDescriptor.Marshaller<InputStream> {

    override fun stream(encryptedStream: InputStream): InputStream {
        return ByteArrayInputStream(AesUtils.aesDecrypt(encryptedStream.readBytes(), passPhrase))
    }

    override fun parse(stream: InputStream?): InputStream? {
        return stream
    }
}