package me.vripper.utilities

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*

object HttpClient {
    val webhookClient = HttpClient(CIO) {
        install(HttpTimeout) {
            // Maximum total time for one request
            requestTimeoutMillis = 30_000

            // Time allowed to establish the connection
            connectTimeoutMillis = 5_000

            // Maximum period waiting for socket activity
            socketTimeoutMillis = 15_000
        }
        install(HttpCookies) {
            // Default in-memory storage:
            storage = AcceptAllCookiesStorage()
        }

        engine {
            // Maximum number of connections in the entire pool
            maxConnectionsCount = 100

            endpoint {
                // Maximum connections to one host/route
                maxConnectionsPerRoute = 20

                // How long an idle persistent connection remains in the pool
                keepAliveTime = 30_000

                // Connection establishment timeout at the engine level
                connectTimeout = 5_000

                // Number of connection attempts
                connectAttempts = 1
            }
        }
    }
}