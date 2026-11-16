package me.vripper.utilities

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC

object LogContext {
    const val THREAD_ID = "threadId"
    const val POST_ID = "postId"

    fun set(threadId: Long? = null, postId: Long? = null) {
        if (threadId != null) MDC.put(THREAD_ID, threadId.toString()) else MDC.remove(THREAD_ID)
        if (postId != null) MDC.put(POST_ID, postId.toString()) else MDC.remove(POST_ID)
    }

    fun clear() {
        MDC.remove(THREAD_ID)
        MDC.remove(POST_ID)
    }

    fun <T> withMdc(threadId: Long? = null, postId: Long? = null, block: () -> T): T {
        val previousThreadId = MDC.get(THREAD_ID)
        val previousPostId = MDC.get(POST_ID)
        try {
            if (threadId != null) MDC.put(THREAD_ID, threadId.toString()) else MDC.remove(THREAD_ID)
            if (postId != null) MDC.put(POST_ID, postId.toString()) else MDC.remove(POST_ID)
            return block()
        } finally {
            restore(THREAD_ID, previousThreadId)
            restore(POST_ID, previousPostId)
        }
    }

    private fun restore(key: String, value: String?) {
        if (value == null) MDC.remove(key) else MDC.put(key, value)
    }

    suspend fun <T> with(threadId: Long? = null, postId: Long? = null, block: suspend () -> T): T {
        val context = MDC.getCopyOfContextMap().orEmpty().toMutableMap()
        if (threadId != null) context[THREAD_ID] = threadId.toString() else context.remove(THREAD_ID)
        if (postId != null) context[POST_ID] = postId.toString() else context.remove(POST_ID)
        return withContext(MDCContext(context)) { block() }
    }
}
