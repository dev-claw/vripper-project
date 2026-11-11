package me.vripper.vgapi

import me.vripper.host.Host
import me.vripper.host.ImgboxHost
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

class VrApiV2ParsingTest {

    @Test
    fun `parses thread metadata, post metadata, images and api version`() {
        val handler = ThreadLookupAPIResponseHandler("https://proxy.example")
        parse(THREAD_XML, handler)
        val result = handler.result

        assertEquals(5531271L, result.threadId)
        assertEquals("Jenna Ortega", result.title)
        assertEquals(161L, result.forumId)
        assertEquals("Celebrity Collections", result.forum)
        assertEquals("Elisha", result.starter)
        assertEquals(259229L, result.starterId)
        assertEquals("2020-09-11T00:48:42Z", result.created)
        assertEquals("2026-11-11T17:20:57Z", result.lastPost)
        assertEquals(268204921L, result.lastPostId)
        assertEquals("stihija", result.lastPoster)
        assertEquals(472, result.replyCount)
        assertEquals(625358, result.views)
        assertEquals(false, result.open)
        assertEquals(true, result.sticky)
        assertEquals(473, result.summaryPosts)
        assertEquals(6841, result.summaryImages)
        assertEquals(268204921L, result.newestPostId)
        assertEquals(2, result.apiVersion)
        assertTrue(result.apiFeatures.containsAll(setOf("since", "subscriptions", "metadata")))
        assertEquals(0, result.pagerAfter)
        assertEquals(268204921L, result.pagerNextAfter)
        assertEquals(false, result.pagerMore)
        assertEquals(500, result.pagerLimit)
        assertEquals(1, result.pagerPagePosts)
        assertEquals("https://proxy.example/threads/5531271", result.url)

        val post = result.postItemList.single()
        assertEquals(70125408L, post.postId)
        assertEquals("Jenna Ortega", post.title)
        assertEquals("Elisha", post.author)
        assertEquals(259229L, post.userId)
        assertEquals("2020-09-11T00:48:42Z", post.date)
        assertEquals("2020-09-12T00:00:00Z", post.edited)
        assertEquals("https://proxy.example/threads/5531271?p=70125408&viewfull=1#post70125408", post.url)
        assertEquals(2, post.imageItemList.size)
        assertEquals(listOf("imgbox" to 2), post.hosts)
        assertEquals("https://imgbox.com/first", post.imageItemList[0].mainLink)
        assertEquals("https://imgbox.com/direct", post.imageItemList[1].mainLink)
        assertEquals("", post.imageItemList[1].thumbLink)
    }

    @Test
    fun `parses subscriptions list`() {
        val handler = SubscriptionsAPIResponseHandler("https://proxy.example")
        parse(SUBSCRIPTIONS_XML, handler)
        val result = handler.result

        assertEquals("", result.error)
        assertEquals(2, result.apiVersion)
        assertTrue(result.apiFeatures.contains("subscriptions"))
        assertEquals(2, result.threadItemList.size)

        val first = result.threadItemList[0]
        assertEquals(5531271L, first.threadId)
        assertEquals("Jenna Ortega", first.title)
        assertEquals("Celebrity Collections", first.forum)
        assertEquals(161L, first.forumId)
        assertEquals(473, first.summaryPosts)
        assertEquals(268204921L, first.newestPostId)
        assertEquals("Elisha", first.starter)
        assertEquals(268204921L, first.lastPostId)
        assertEquals("https://proxy.example/threads/5531271", first.url)
        assertTrue(first.postItemList.isEmpty())

        val second = result.threadItemList[1]
        assertEquals(5531299L, second.threadId)
        assertEquals("Another thread", second.title)
        assertEquals(268204999L, second.lastPostId)
    }

    @Test
    fun `parses permission errors`() {
        val handler = SubscriptionsAPIResponseHandler("https://proxy.example")
        parse(ERROR_XML, handler)

        assertEquals("Login Required - Access Denied!", handler.result.error)
        assertEquals("permissions", handler.result.errorType)
        assertTrue(handler.result.threadItemList.isEmpty())
    }

    @Test
    fun `rejects thread response without a thread element`() {
        val handler = ThreadLookupAPIResponseHandler("https://proxy.example")
        parse(MISSING_THREAD_XML, handler)

        assertEquals("Malformed response: missing thread", handler.result.error)
        assertEquals("protocol", handler.result.errorType)
    }

    @Test
    fun `rejects thread response with an unsupported api version`() {
        val handler = ThreadLookupAPIResponseHandler("https://proxy.example")
        parse(THREAD_XML.replace("version=\"2\"", "version=\"1\""), handler)

        assertEquals("Unsupported API version 1", handler.result.error)
        assertEquals("protocol", handler.result.errorType)
    }

    @Test
    fun `rejects thread response for a different thread id`() {
        val handler = ThreadLookupAPIResponseHandler(
            "https://proxy.example",
            expectedThreadId = 42L,
        )
        parse(THREAD_XML, handler)

        assertEquals(
            "Response thread id 5531271 does not match requested 42",
            handler.result.error,
        )
        assertEquals("protocol", handler.result.errorType)
    }

    @Test
    fun `rejects subscriptions response without a subscriptions element`() {
        val handler = SubscriptionsAPIResponseHandler("https://proxy.example")
        parse(MISSING_SUBSCRIPTIONS_XML, handler)

        assertEquals("Malformed response: missing subscriptions", handler.result.error)
        assertEquals("protocol", handler.result.errorType)
    }

    @Test
    fun `rejects subscriptions response with an unsupported api version`() {
        val handler = SubscriptionsAPIResponseHandler("https://proxy.example")
        parse(SUBSCRIPTIONS_XML.replace("version=\"2\"", "version=\"1\""), handler)

        assertEquals("Unsupported API version 1", handler.result.error)
        assertEquals("protocol", handler.result.errorType)
    }

    @Test
    fun `does not mutate the seeded seen links`() {
        val seeded = mutableSetOf("https://imgbox.com/first")
        val handler = ThreadLookupAPIResponseHandler("https://proxy.example", seeded)

        parse(THREAD_XML, handler)

        assertEquals(setOf("https://imgbox.com/first"), seeded)
    }

    @Test
    fun `uses seeded seen links to suppress duplicate images`() {
        val seeded = setOf("https://imgbox.com/first")
        val handler = ThreadLookupAPIResponseHandler("https://proxy.example", seeded)

        parse(THREAD_XML, handler)

        assertEquals(
            setOf(
                "https://imgbox.com/first",
                "https://imgbox.com/duplicate",
                "https://imgbox.com/direct",
            ),
            handler.seenLinks(),
        )
        assertEquals(
            listOf("https://imgbox.com/direct"),
            handler.result.postItemList.single().imageItemList.map { it.mainLink },
        )
    }

    private fun parse(xml: String, handler: org.xml.sax.helpers.DefaultHandler) {
        ByteArrayInputStream(xml.toByteArray()).use {
            SAXParserFactory.newInstance().newSAXParser().parse(it, handler)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun start() {
            startKoin {
                modules(module {
                    single { ImgboxHost() } bind Host::class
                })
            }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            stopKoin()
        }

        private val THREAD_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <images>
                <user hash="guest" />
                <forum title="Celebrity Collections" id="161" />
                <thread id="5531271" title="Jenna Ortega" prefixid="" starter="Elisha" starterid="259229" created="2020-09-11T00:48:42Z" lastpost="2026-11-11T17:20:57Z" lastpostid="268204921" lastposter="stihija" replycount="472" views="625358" open="0" sticky="1" tags="" url="https://vipergirls.to/showthread.php?t=5531271" />
                <post id="70125408" number="1" title="" prefixid="" imagecount="3" author="Elisha" userid="259229" date="2020-09-11T00:48:42Z" edited="2020-09-12T00:00:00Z" url="https://vipergirls.to/showthread.php?p=70125408#post70125408" >
                    <image type="linked" thumb_url="https://thumbs2.imgbox.com/a_t.jpg" main_url="https://imgbox.com/first" host="imgbox.com" duplicate="0" />
                    <image type="linked" thumb_url="https://thumbs2.imgbox.com/a_t.jpg" main_url="https://imgbox.com/first" host="imgbox.com" duplicate="0" />
                    <image type="linked" thumb_url="https://thumbs2.imgbox.com/b_t.jpg" main_url="https://imgbox.com/duplicate" host="imgbox.com" duplicate="1" />
                    <image type="directlinked" thumb_url="" main_url="https://imgbox.com/direct" host="imgbox.com" duplicate="0" />
                </post>
                <summary posts="473" images="6841" newest_postid="268204921" />
                <pager after="0" next_after="268204921" more="0" limit="500" page_posts="1" />
                <api version="2" features="since,quotes,dedupe,metadata,subscriptions,etag,gzip" />
            </images>
        """.trimIndent()

        private val SUBSCRIPTIONS_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <images>
                <user hash="abc123" />
                <subscriptions count="2">
                    <thread id="5531271" title="Jenna Ortega" prefixid="" starter="Elisha" starterid="259229" created="2020-09-11T00:48:42Z" lastpost="2026-11-11T17:20:57Z" lastpostid="268204921" lastposter="stihija" replycount="472" views="625358" open="1" sticky="0" forumid="161" forum="Celebrity Collections" folderid="12" />
                    <thread id="5531299" title="Another thread" prefixid="" starter="Someone" starterid="42" created="2021-01-01T00:00:00Z" lastpost="2026-11-10T10:00:00Z" lastpostid="268204999" lastposter="Other" replycount="10" views="20" open="1" sticky="1" forumid="15" forum="Care" folderid="0" />
                </subscriptions>
                <api version="2" features="since,subscriptions,metadata" />
            </images>
        """.trimIndent()

        private val MISSING_THREAD_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <images>
                <user hash="guest" />
                <api version="2" features="metadata" />
            </images>
        """.trimIndent()

        private val MISSING_SUBSCRIPTIONS_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <images>
                <user hash="abc123" />
                <api version="2" features="subscriptions" />
            </images>
        """.trimIndent()

        private val ERROR_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <images>
                <user hash="guest" />
                <error type="permissions" details="Login Required - Access Denied!" />
            </images>
        """.trimIndent()
    }
}
