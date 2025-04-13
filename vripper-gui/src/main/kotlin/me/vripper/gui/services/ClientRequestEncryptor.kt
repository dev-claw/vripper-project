package me.vripper.gui.services

import io.grpc.MethodDescriptor
import me.vripper.utilities.AesUtils
import java.io.ByteArrayInputStream
import java.io.InputStream


class ClientRequestEncryptor<T>(
    private val protosMarshaller: MethodDescriptor.Marshaller<T>,
    private val passPhrase: String
) :
    MethodDescriptor.Marshaller<T> {

    override fun stream(value: T): InputStream {
        return ByteArrayInputStream(AesUtils.aesEncrypt(protosMarshaller.stream(value).readBytes(), passPhrase))
    }

    override fun parse(stream: InputStream): T {
        return protosMarshaller.parse(stream)
    }
}