package me.vripper.vgapi

import me.vripper.host.Host
import org.koin.core.component.KoinComponent
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal class ThreadLookupAPIResponseHandler(private val siteProxy: String) : KoinComponent, DefaultHandler() {
    private val supportedHosts: List<Host> = getKoin().getAll()
    private var error: String = ""
    private val hostMap: MutableMap<Host, Int> = mutableMapOf()
    private val postItemList: MutableList<PostItem> = mutableListOf()
    private val imageItemList: MutableList<ImageItem> = mutableListOf()
    private lateinit var threadItem: ThreadItem
    private var threadId: Long = -1
    private var threadTitle: String = ""
    private var forum: String = ""
    private var securityToken: String = ""
    private var postId: Long = -1
    private var postTitle: String = ""

    private var postCounter: Int = 0
    val result: ThreadItem
        get() = threadItem

    override fun startDocument() {}
    override fun startElement(
        uri: String, localName: String, qName: String, attributes: Attributes
    ) {
        when (qName.lowercase()) {
            "error" -> error = attributes.getValue("details")
            "thread" -> {
                threadId = attributes.getValue("id")?.trim()?.toLong() ?: -1
                threadTitle = attributes.getValue("title")?.trim() ?: ""
            }

            "forum" -> forum = attributes.getValue("title")?.trim() ?: ""
            "user" -> securityToken = attributes.getValue("hash")?.trim() ?: ""
            "post" -> {
                postId = attributes.getValue("id")?.trim()?.toLong() ?: -1
                postCounter = attributes.getValue("number")?.trim()?.toInt() ?: 0
                val title = attributes.getValue("title")?.trim() ?: ""
                postTitle = title.ifBlank { threadTitle }
            }

            "image" -> {
                val mainLink = attributes.getValue("main_url")?.trim() ?: ""
                val thumbLink = attributes.getValue("thumb_url")?.trim() ?: ""
                val type = attributes.getValue("type")?.trim() ?: ""
                if (type == "linked") {
                    supportedHosts.firstOrNull {
                        it.isSupported(mainLink)
                    }?.also { host ->
                        hostMap.computeIfAbsent(host) { 0 }
                        hostMap[host] = hostMap[host]!! + 1
                        imageItemList.add(ImageItem(mainLink, thumbLink, host))
                    }
                }
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
                        "$siteProxy/threads/$threadId?p=$postId&viewfull=1#post$postId",
                        hostMap.toMap().map { Pair(it.key.hostName, it.value) },
                        securityToken,
                        forum,
                        imageItemList.toList()
                    )
                )
            }
            imageItemList.clear()
            hostMap.clear()
        }
    }

    override fun endDocument() {
        threadItem = ThreadItem(threadId, threadTitle, securityToken, forum, postItemList.toList(), error)
    }
}