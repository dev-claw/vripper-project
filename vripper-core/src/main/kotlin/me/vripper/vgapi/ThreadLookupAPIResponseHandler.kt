package me.vripper.vgapi

import me.vripper.host.Host
import me.vripper.utilities.ViperLinks
import org.koin.core.component.KoinComponent
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal class ThreadLookupAPIResponseHandler(
    private val siteProxy: String,
    alreadySeenMainLinks: Set<String> = emptySet(),
    private val expectedThreadId: Long? = null,
) : KoinComponent, DefaultHandler() {
    private val seenMainLinks: MutableSet<String> = alreadySeenMainLinks.toMutableSet()
    private val supportedHosts: List<Host> = getKoin().getAll()
    private var error: String = ""
    private var errorType: String = ""
    private var forum: String = ""
    private var forumId: Long = -1
    private var securityToken: String = ""
    private var threadId: Long = -1
    private var threadTitle: String = ""
    private var threadPrefixId: String = ""
    private var threadStarter: String = ""
    private var threadStarterId: Long = -1
    private var threadCreated: String = ""
    private var threadLastPost: String = ""
    private var threadLastPostId: Long = -1
    private var threadLastPoster: String = ""
    private var threadReplyCount: Int = 0
    private var threadViews: Int = 0
    private var threadOpen: Boolean = true
    private var threadSticky: Boolean = false
    private var summaryPosts: Int = 0
    private var summaryImages: Int = 0
    private var newestPostId: Long = 0
    private var apiVersion: Int = 0
    private var apiFeatures: Set<String> = emptySet()
    private var pagerAfter: Long = 0
    private var pagerNextAfter: Long = 0
    private var pagerMore: Boolean = false
    private var pagerLimit: Int = 0
    private var pagerPagePosts: Int = 0
    private var postId: Long = -1
    private var postTitle: String = ""
    private var postPrefixId: String = ""
    private var postAuthor: String = ""
    private var postUserId: Long = -1
    private var postDate: String = ""
    private var postEdited: String = ""
    private var postCounter: Int = 0
    private val hostMap: MutableMap<Host, Int> = mutableMapOf()
    private val postItemList: MutableList<PostItem> = mutableListOf()
    private val imageItemList: MutableList<ImageItem> = mutableListOf()
    private lateinit var threadItem: ThreadItem

    val result: ThreadItem
        get() = threadItem

    fun seenLinks(): Set<String> = seenMainLinks

    override fun startDocument() {}
    override fun startElement(
        uri: String, localName: String, qName: String, attributes: Attributes
    ) {
        when (qName.lowercase()) {
            "error" -> {
                error = attributes.string("details")
                errorType = attributes.string("type")
            }

            "thread" -> {
                threadId = attributes.long("id")
                threadTitle = attributes.string("title")
                threadPrefixId = attributes.string("prefixid")
                threadStarter = attributes.string("starter")
                threadStarterId = attributes.long("starterid")
                threadCreated = attributes.string("created")
                threadLastPost = attributes.string("lastpost")
                threadLastPostId = attributes.long("lastpostid")
                threadLastPoster = attributes.string("lastposter")
                threadReplyCount = attributes.int("replycount")
                threadViews = attributes.int("views")
                threadOpen = attributes.flag("open", true)
                threadSticky = attributes.flag("sticky", false)
            }

            "forum" -> {
                forum = attributes.string("title")
                forumId = attributes.long("id")
            }

            "user" -> securityToken = attributes.string("hash")
            "post" -> {
                postId = attributes.long("id")
                postCounter = attributes.int("number")
                postPrefixId = attributes.string("prefixid")
                postAuthor = attributes.string("author")
                postUserId = attributes.long("userid")
                postDate = attributes.string("date")
                postEdited = attributes.string("edited")
                val title = attributes.string("title")
                postTitle = title.ifBlank { threadTitle }
            }

            "image" -> {
                val mainLink = attributes.string("main_url").replace("http://", "https://")
                val thumbLink = attributes.string("thumb_url").replace("http://", "https://")
                val type = attributes.string("type").lowercase()
                val hostName = attributes.string("host").lowercase()
                val duplicate = attributes.flag("duplicate", false)
                if (mainLink.isNotBlank() && (type == "linked" || type == "directlinked")) {
                    val alreadySeen = !seenMainLinks.add(mainLink)
                    val host = supportedHosts.firstOrNull {
                        hostName.isNotEmpty() && it.isSupported(hostName)
                    } ?: supportedHosts.firstOrNull { it.isSupported(mainLink) }
                    if (host != null && !duplicate && !alreadySeen) {
                        hostMap.computeIfAbsent(host) { 0 }
                        hostMap[host] = hostMap[host]!! + 1
                        imageItemList.add(
                            ImageItem(
                                mainLink = mainLink,
                                thumbLink = if (type == "linked") thumbLink else "",
                                host = host,
                                hostName = hostName,
                                duplicate = false,
                            )
                        )
                    }
                }
            }

            "summary" -> {
                summaryPosts = attributes.int("posts")
                summaryImages = attributes.int("images")
                newestPostId = attributes.long("newest_postid")
            }

            "pager" -> {
                pagerAfter = attributes.long("after")
                pagerNextAfter = attributes.long("next_after")
                pagerMore = attributes.flag("more", false)
                pagerLimit = attributes.int("limit")
                pagerPagePosts = attributes.int("page_posts")
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

    override fun endElement(uri: String, localName: String, qName: String) {
        if ("post".equals(qName, true)) {
            if (imageItemList.isNotEmpty()) {
                postItemList.add(
                    PostItem(
                        threadId,
                        threadTitle,
                        postId,
                        postCounter,
                        postTitle,
                        imageItemList.size,
                        ViperLinks.postUrl(siteProxy, threadId, postId),
                        hostMap.toMap().map { Pair(it.key.hostName, it.value) },
                        securityToken,
                        forum,
                        imageItemList.toList(),
                        postPrefixId,
                        postAuthor,
                        postUserId,
                        postDate,
                        postEdited,
                    )
                )
            }
            imageItemList.clear()
            hostMap.clear()
        }
    }

    override fun endDocument() {
        if (error.isBlank()) {
            when {
                apiVersion < 2 -> {
                    error = "Unsupported API version $apiVersion"
                    errorType = "protocol"
                }

                threadId <= 0 -> {
                    error = "Malformed response: missing thread"
                    errorType = "protocol"
                }

                expectedThreadId != null && threadId != expectedThreadId -> {
                    error = "Response thread id $threadId does not match requested $expectedThreadId"
                    errorType = "protocol"
                }
            }
        }
        threadItem = ThreadItem(
            threadId = threadId,
            title = threadTitle,
            securityToken = securityToken,
            forum = forum,
            postItemList = postItemList.toList(),
            error = error,
            errorType = errorType,
            forumId = forumId,
            prefixId = threadPrefixId,
            starter = threadStarter,
            starterId = threadStarterId,
            created = threadCreated,
            lastPost = threadLastPost,
            lastPostId = threadLastPostId,
            lastPoster = threadLastPoster,
            replyCount = threadReplyCount,
            views = threadViews,
            open = threadOpen,
            sticky = threadSticky,
            url = if (threadId > 0) ViperLinks.threadUrl(siteProxy, threadId) else "",
            summaryPosts = summaryPosts,
            summaryImages = summaryImages,
            newestPostId = newestPostId.takeIf { it > 0 } ?: threadLastPostId.takeIf { it > 0 } ?: 0,
            apiVersion = apiVersion,
            apiFeatures = apiFeatures,
            pagerAfter = pagerAfter,
            pagerNextAfter = pagerNextAfter,
            pagerMore = pagerMore,
            pagerLimit = pagerLimit,
            pagerPagePosts = pagerPagePosts,
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
