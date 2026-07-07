package me.vripper.ui

import com.squareup.wire.GrpcException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val STREAM_RETRY_DELAY_MS = 2_000L

/**
 * Collects a server stream and automatically re-subscribes when the stream
 * fails. Server streams are long lived, so transient failures should not tear
 * down the screen.
 */
internal fun <T> CoroutineScope.observeStream(
    source: () -> Flow<T>,
    onValue: (T) -> Unit,
    onError: (Throwable) -> Unit = {},
) {
    launch {
        while (isActive) {
            try {
                source().collect(onValue)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                onError(t)
            }
            delay(STREAM_RETRY_DELAY_MS)
        }
    }
}

internal fun Throwable.userMessage(fallback: String): String {
    if (this is GrpcException) {
        val description = grpcMessage?.takeIf { it.isNotBlank() }
        return when (grpcStatus.name) {
            "UNAVAILABLE" -> "Cannot reach the server"
            "DEADLINE_EXCEEDED" -> "The server took too long to respond"
            else -> description ?: fallback
        }
    }
    val raw = message?.trim().orEmpty()
    val lower = raw.lowercase()
    return when {
        lower.contains("aeadbadtag") ||
            lower.contains("badpadding") ||
            lower.contains("tag mismatch") -> "Wrong pass phrase"

        lower.contains("unavailable") ||
            lower.contains("connectexception") ||
            lower.contains("unknownhost") -> "Cannot reach the server"

        lower.contains("timeout") || lower.contains("timed out") ->
            "The server took too long to respond"

        raw.isBlank() || raw.startsWith("grpc-status") -> fallback
        else -> raw
    }
}
