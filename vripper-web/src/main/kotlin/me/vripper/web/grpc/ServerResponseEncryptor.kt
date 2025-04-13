package me.vripper.web.grpc

import io.grpc.MethodDescriptor
import me.vripper.utilities.AesUtils
import java.io.ByteArrayInputStream
import java.io.InputStream


class ServerResponseEncryptor(private val passPhrase: String) : MethodDescriptor.Marshaller<InputStream> {

    override fun stream(serializedProto: InputStream): InputStream {
        return ByteArrayInputStream(AesUtils.aesEncrypt(serializedProto.readBytes(), passPhrase))
    }

    override fun parse(stream: InputStream?): InputStream? {
        return stream
    }
}