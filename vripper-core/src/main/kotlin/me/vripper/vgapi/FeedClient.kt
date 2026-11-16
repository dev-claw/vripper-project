package me.vripper.vgapi

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import me.vripper.exception.DownloadException
import me.vripper.services.HTTPService
import me.vripper.services.VGAuthService
import me.vripper.utilities.ApplicationProperties
import me.vripper.utilities.LogContext
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.RequestLimit
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.net.URIBuilder
import java.util.concurrent.TimeUnit

internal class FeedClient(
    private val httpService: HTTPService,
    private val vgAuthService: VGAuthService,
) {
    private val log by LoggerDelegate()
    private val cache: Cache<String, CachedFeed> = Caffeine.newBuilder()
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .maximumSize(64)
        .build()

    fun invalidate() {
        cache.invalidateAll()
    }

    fun get(
        parameters: Map<String, String>,
        threadId: Long? = null,
        postId: Long? = null,
    ): FeedResponse = LogContext.withMdc(
        threadId = threadId ?: parameters["threadid"]?.toLongOrNull(),
        postId = postId ?: parameters["postid"]?.toLongOrNull(),
    ) {
        val requestParameters = DEFAULT_PARAMETERS + parameters
        val key = cacheKey(requestParameters)
        val cached = cache.getIfPresent(key)
        val request = HttpGet(
            URIBuilder(ApplicationProperties.VR_FEED_URL).also { builder ->
                requestParameters.forEach { (name, value) -> builder.setParameter(name, value) }
            }.build()
        ).also {
            it.setAbsoluteRequestUri(true)
            it.addHeader("Accept-Encoding", "gzip")
            cached?.etag?.let { etag -> it.addHeader("If-None-Match", etag) }
        }
        log.info("{}", request)
        RequestLimit.getPermit(1)
        httpService.client.execute(request, vgAuthService.createClickContext()) { response ->
            val etag = response.getFirstHeader("ETag")?.value
            val userHash = response.getFirstHeader("X-User-Hash")?.value
            val apiVersion = response.getFirstHeader("X-Api-Version")?.value
            when (response.code) {
                304 -> {
                    val body = cached?.body
                        ?: throw DownloadException("Received 304 without a cached response for $request")
                    FeedResponse(body, etag ?: cached.etag, userHash, apiVersion, true)
                }

                in 200..299 -> {
                    val body = EntityUtils.toByteArray(response.entity)
                    if (etag != null) {
                        cache.put(key, CachedFeed(body, etag))
                    }
                    FeedResponse(body, etag, userHash, apiVersion, false)
                }

                else -> throw DownloadException("Unexpected response code '${response.code}' for $request")
            }
        }
    }

    private fun cacheKey(parameters: Map<String, String>): String =
        buildFeedCacheKey(
            ApplicationProperties.VR_FEED_URL,
            vgAuthService.cacheIdentity(),
            parameters,
        )

    private data class CachedFeed(val body: ByteArray, val etag: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CachedFeed

            if (!body.contentEquals(other.body)) return false
            if (etag != other.etag) return false

            return true
        }

        override fun hashCode(): Int {
            var result = body.contentHashCode()
            result = 31 * result + etag.hashCode()
            return result
        }
    }

    companion object {
        private val DEFAULT_PARAMETERS = mapOf(
            "quotes" to "skip",
            "dedupe" to "1",
        )
    }
}

internal fun buildFeedCacheKey(
    feedUrl: String,
    authIdentity: String,
    parameters: Map<String, String>,
): String =
    buildString {
        append(feedUrl)
        append('|')
        append(authIdentity)
        append('|')
        append(parameters.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" })
    }

internal data class FeedResponse(
    val body: ByteArray,
    val etag: String?,
    val userHash: String?,
    val apiVersion: String?,
    val notModified: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FeedResponse

        if (notModified != other.notModified) return false
        if (!body.contentEquals(other.body)) return false
        if (etag != other.etag) return false
        if (userHash != other.userHash) return false
        if (apiVersion != other.apiVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = notModified.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + etag.hashCode()
        result = 31 * result + userHash.hashCode()
        result = 31 * result + apiVersion.hashCode()
        return result
    }
}
