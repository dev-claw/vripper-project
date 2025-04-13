package me.vripper.services

import kotlinx.coroutines.*
import me.vripper.utilities.ApplicationProperties
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.cookie.StandardCookieSpec
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.io.HttpClientConnectionManager
import org.apache.hc.core5.pool.PoolConcurrencyPolicy
import org.apache.hc.core5.pool.PoolReusePolicy
import org.apache.hc.core5.util.Timeout

internal class HTTPService {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var pcm: HttpClientConnectionManager = BasicHttpClientConnectionManager()
    private var rc: RequestConfig = RequestConfig.DEFAULT
    private var cc: ConnectionConfig = ConnectionConfig.DEFAULT
    private var connectionExpiryJob: Job? = null
    var client: CloseableHttpClient = HttpClients.createDefault()

    fun buildConnectionPool() {
        pcm.close()
        connectionExpiryJob?.cancel()
        pcm = PoolingHttpClientConnectionManagerBuilder.create()
            .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
            .setConnPoolPolicy(PoolReusePolicy.FIFO)
            .setDefaultConnectionConfig(cc)
            .setMaxConnTotal(Int.MAX_VALUE)
            .setMaxConnPerRoute(Int.MAX_VALUE)
            .build().also {
                connectionExpiryJob = coroutineScope.launch {
                    while (isActive) {
                        it.closeExpired()
                        delay(60_000)
                    }
                }
            }
    }

    fun buildRequestConfig(connectionTimeout: Long) {
        rc = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(connectionTimeout))
            .setCookieSpec(StandardCookieSpec.RELAXED)
            .build()
    }

    fun buildConnectionConfig(connectionTimeout: Long) {
        cc = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(connectionTimeout))
            .setSocketTimeout(Timeout.ofSeconds(connectionTimeout))
            .build()
    }

    fun buildClientBuilder() {
        client = HttpClients.custom()
            .setConnectionManager(pcm)
            .setRedirectStrategy(DefaultRedirectStrategy.INSTANCE)
            .setUserAgent(ApplicationProperties.USER_AGENT)
            .disableAutomaticRetries()
            .setDefaultRequestConfig(rc)
            .build()
    }
}