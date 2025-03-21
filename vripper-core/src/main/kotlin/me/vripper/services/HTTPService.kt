package me.vripper.services

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import me.vripper.event.EventBus
import me.vripper.event.SettingsUpdateEvent
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

internal class HTTPService(
    private val eventBus: EventBus
) {

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/118.0"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var pcm: HttpClientConnectionManager = BasicHttpClientConnectionManager()
    private var rc: RequestConfig = RequestConfig.DEFAULT
    private var cc: ConnectionConfig = ConnectionConfig.DEFAULT
    private var connectionTimeout = 30
    private var connectionExpiryJob: Job? = null
    var client: CloseableHttpClient = HttpClients.createDefault()

    fun init() {
        coroutineScope.launch {
            eventBus
                .events
                .filterIsInstance(SettingsUpdateEvent::class)
                .collect {
                    if (connectionTimeout != it.settings.connectionSettings.timeout) {
                        connectionTimeout = it.settings.connectionSettings.timeout
                        client.close()
                        pcm.close()
                        buildRequestConfig()
                        buildConnectionConfig()
                        buildConnectionPool()
                        buildClientBuilder()
                    }
                }
        }
    }

    private fun buildConnectionPool() {
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

    private fun buildRequestConfig() {
        rc = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(connectionTimeout.toLong()))
            .setCookieSpec(StandardCookieSpec.RELAXED)
            .build()
    }

    private fun buildConnectionConfig() {
        cc = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(connectionTimeout.toLong()))
            .setSocketTimeout(Timeout.ofSeconds(connectionTimeout.toLong()))
            .build()
    }

    private fun buildClientBuilder() {
        client = HttpClients.custom()
            .setConnectionManager(pcm)
            .setRedirectStrategy(DefaultRedirectStrategy.INSTANCE)
            .setUserAgent(USER_AGENT)
            .disableAutomaticRetries()
            .setDefaultRequestConfig(rc)
            .build()
    }
}