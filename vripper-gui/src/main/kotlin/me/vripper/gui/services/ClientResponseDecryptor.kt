package me.vripper.gui.services

import io.grpc.MethodDescriptor
import me.vripper.utilities.AesUtils
import java.io.ByteArrayInputStream
import java.io.InputStream


class ClientResponseDecryptor<T>(
    private val protoMarshaller: MethodDescriptor.Marshaller<T>,
    private val passPhrase: String
) :
    MethodDescriptor.Marshaller<T> {

    override fun stream(value: T): InputStream {
        return protoMarshaller.stream(value)
    }

    override fun parse(encryptedStream: InputStream): T {
        return protoMarshaller.parse(ByteArrayInputStream(AesUtils.aesDecrypt(encryptedStream.readBytes(), passPhrase)))
    }
}