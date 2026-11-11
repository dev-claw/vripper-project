package me.vripper.utilities

object ViperLinks {
    fun threadUrl(siteProxy: String, threadId: Long): String {
        return "${siteProxy.trimEnd('/')}/threads/$threadId"
    }

    fun postUrl(siteProxy: String, threadId: Long, postId: Long): String {
        return "${threadUrl(siteProxy, threadId)}?p=$postId&viewfull=1#post$postId"
    }
}
