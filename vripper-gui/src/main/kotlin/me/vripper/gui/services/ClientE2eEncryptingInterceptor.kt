package me.vripper.gui.services

import io.grpc.*
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall

class ClientE2eEncryptingInterceptor(private val passPhrase: String) : ClientInterceptor {

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT?, RespT?>,
        callOptions: CallOptions?, next: Channel
    ): ClientCall<ReqT, RespT> {
        return object : SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(
                method.toBuilder(
                    ClientRequestEncryptor(method.getRequestMarshaller(), passPhrase),
                    ClientResponseDecryptor(method.getResponseMarshaller(), passPhrase)
                ).build(),
                callOptions
            )
        ) {}
    }
}