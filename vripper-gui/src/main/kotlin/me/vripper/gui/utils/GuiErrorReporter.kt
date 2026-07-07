package me.vripper.gui.utils

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CancellationException
import me.vripper.gui.event.GuiEventBus
import me.vripper.utilities.LoggerDelegate

object GuiErrorReporter {

    private val log by LoggerDelegate()

    fun report(source: String, throwable: Throwable) {
        if (AppEndpointManager.currentAppState() == GuiEventBus.ChangingSession && isExpectedShutdown(throwable)) {
            log.debug("Ignoring expected gRPC shutdown error during session change: $source")
            return
        }
        val message = describe(throwable)
        log.error("Endpoint error [$source]: $message", throwable)
        GuiEventBus.tryPublishEvent(GuiEventBus.RemoteError(source, message))
    }

    suspend fun <T> call(source: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            report(source, t)
            Result.failure(t)
        }
    }

    fun message(throwable: Throwable): String = describe(throwable)

    private fun isExpectedShutdown(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause }.any { cause ->
            val status = when (cause) {
                is StatusRuntimeException -> cause.status
                is StatusException -> cause.status
                else -> null
            }
            status?.code == Status.Code.UNAVAILABLE && status.description == "Channel shutdownNow invoked"
        }
    }

    private fun describe(throwable: Throwable): String {
        val statusDescription = when (throwable) {
            is StatusRuntimeException -> throwable.status.let { status ->
                status.description?.takeIf { it.isNotBlank() }?.let { "${status.code.name}: $it" }
                    ?: status.code.name
            }

            is StatusException -> throwable.status.let { status ->
                status.description?.takeIf { it.isNotBlank() }?.let { "${status.code.name}: $it" }
                    ?: status.code.name
            }

            else -> null
        }
        return statusDescription
            ?.takeIf { it.isNotBlank() }
            ?: throwable.message?.takeIf { it.isNotBlank() }
            ?: throwable::class.simpleName
            ?: "Unknown error"
    }
}
