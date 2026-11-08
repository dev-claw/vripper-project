package me.vripper.image

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val thumbnailClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

actual suspend fun fetchThumbnailBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        thumbnailClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext null
            }
            response.body.bytes().takeIf { it.isNotEmpty() }
        }
    }.getOrNull()
}

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0 Mobile Safari/537.36 VRipperMobile"
