package me.vripper.vgapi

import me.vripper.utilities.ViperLinks
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal class SubscriptionsAPIResponseHandler(private val siteProxy: String) : DefaultHandler() {
    private var error: String = ""
    private var errorType: String = ""
    private var apiVersion: Int = 0
    private var apiFeatures: Set<String> = emptySet()
    private val threadItemList: MutableList<ThreadItem> = mutableListOf()
    private var sawSubscriptions: Boolean = false
    private lateinit var subscriptionsItem: SubscriptionsItem

    val result: SubscriptionsItem
        get() = subscriptionsItem

    override fun startElement(
        uri: String, localName: String, qName: String, attributes: Attributes
    ) {
        when (qName.lowercase()) {
            "error" -> {
                error = attributes.string("details")
                errorType = attributes.string("type")
            }

            "subscriptions" -> {
                sawSubscriptions = true
            }

            "thread" -> {
                val threadId = attributes.long("id")
                if (threadId > 0) {
                    val lastPostId = attributes.long("lastpostid")
                    val replyCount = attributes.int("replycount")
                    threadItemList.add(
                        ThreadItem(
                            threadId = threadId,
                            title = attributes.string("title"),
                            securityToken = "",
                            forum = attributes.string("forum"),
                            postItemList = emptyList(),
                            error = "",
                            forumId = attributes.long("forumid"),
                            prefixId = attributes.string("prefixid"),
                            starter = attributes.string("starter"),
                            starterId = attributes.long("starterid"),
                            created = attributes.string("created"),
                            lastPost = attributes.string("lastpost"),
                            lastPostId = lastPostId,
                            lastPoster = attributes.string("lastposter"),
                            replyCount = replyCount,
                            views = attributes.int("views"),
                            open = attributes.flag("open", true),
                            sticky = attributes.flag("sticky", false),
                            url = ViperLinks.threadUrl(siteProxy, threadId),
                            summaryPosts = if (lastPostId > 0) replyCount + 1 else 0,
                            newestPostId = lastPostId.takeIf { it > 0 } ?: 0,
                        )
                    )
                }
            }

            "api" -> {
                apiVersion = attributes.int("version")
                apiFeatures = attributes.string("features")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        }
    }

    override fun endDocument() {
        if (error.isBlank()) {
            when {
                apiVersion < 2 -> {
                    error = "Unsupported API version $apiVersion"
                    errorType = "protocol"
                }

                !sawSubscriptions -> {
                    error = "Malformed response: missing subscriptions"
                    errorType = "protocol"
                }
            }
        }
        subscriptionsItem = SubscriptionsItem(
            threadItemList = threadItemList.toList(),
            error = error,
            errorType = errorType,
            apiVersion = apiVersion,
            apiFeatures = apiFeatures,
        )
    }

    private fun Attributes.string(name: String): String = getValue(name)?.trim() ?: ""

    private fun Attributes.long(name: String): Long = string(name).toLongOrNull() ?: -1

    private fun Attributes.int(name: String): Int = string(name).toIntOrNull() ?: 0

    private fun Attributes.flag(name: String, default: Boolean): Boolean =
        when (string(name).lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> default
        }
}
