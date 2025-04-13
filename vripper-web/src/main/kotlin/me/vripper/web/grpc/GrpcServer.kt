package me.vripper.web.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerInterceptors
import me.vripper.utilities.LoggerDelegate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy


@Component
class GrpcServer(
    @Value("\${grpc.enabled}") private val enabled: Boolean,
    @Value("\${grpc.port}") private val port: Int,
    @Value("\${grpc.passphrase}") private val passPhrase: String,
    grpcServerAppEndpointService: GrpcServerAppEndpointService
) {

    private val log by LoggerDelegate()

    private val server: Server =
        ServerBuilder
            .forPort(port)
            .addService(
                ServerInterceptors.intercept(
                    ServerInterceptors.useMarshalledMessages(
                        grpcServerAppEndpointService.bindService(),
                        ServerRequestDecryptor(passPhrase),
                        ServerResponseEncryptor(passPhrase)
                    )
                )
            )
            .build()

    @PostConstruct
    fun start() {
        if (enabled) {
            log.info("gRPC is enabled, starting gRPC server on port $port")
            server.start()
        } else {
            log.info("gRPC is disabled, remote connection to this instance is not possible")
        }
    }

    @PreDestroy
    fun destroy() {
        server.shutdown()
    }
}