package me.vripper.tasks

import me.vripper.entities.MetadataEntity
import me.vripper.entities.PostEntity
import me.vripper.exception.DownloadException
import me.vripper.exception.VripperException
import me.vripper.services.DataAccessService
import me.vripper.services.HTTPService
import me.vripper.services.SettingsService
import me.vripper.services.VGAuthService
import me.vripper.utilities.HtmlUtils
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.RequestLimit
import me.vripper.utilities.XpathUtils
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.net.URIBuilder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.Node
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors

internal class FetchMetadataTask(
    private val postEntity: PostEntity,
) : KoinComponent, ITask {
    private val dictionary: List<String> = mutableListOf("download", "link", "rapidgator", "filefactory", "filefox")
    private val log by LoggerDelegate()
    private val settingsService: SettingsService by inject()
    private val vgAuthService: VGAuthService by inject()
    private val httpService: HTTPService by inject()
    private val dataAccessService: DataAccessService by inject()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Fetching metadata"

    override fun getCategory() = TaskCategory.METADATA

    override fun getDescription() = "Fetching metadata for post #${postEntity.vgPostId}"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        if (!settingsService.settings.viperSettings.fetchMetadata) {
            log.debug("Fetching metadata is disabled")
            status = TaskStatus.WARNING
            result = "Fetching metadata is disabled"
            return
        }
        try {
            val httpGet = HttpGet(URIBuilder(settingsService.settings.viperSettings.host + "/threads/").also {
                it.setParameter(
                    "p", postEntity.vgPostId.toString()
                )
            }.build()).also { it.setAbsoluteRequestUri(true) }

            RequestLimit.getPermit(1)
            log.info("{}", httpGet)
            val response = httpService.client.execute(httpGet, vgAuthService.createVgContext()) {
                if (it.code / 100 != 2) {
                    throw DownloadException("Unexpected response code '${it.code}' for $httpGet")
                }
                EntityUtils.toString(it.entity)
            }

            val document = HtmlUtils.clean(response)
            val postNode: Node = XpathUtils.getAsNode(
                document,
                "//li[@id='post_${postEntity.vgPostId}']/div[contains(@class, 'postdetails')]",

                ) ?: throw VripperException("Unable to find post #'${postEntity.vgPostId}'")

            val postedBy: String = XpathUtils.getAsNode(
                postNode, "./div[contains(@class, 'userinfo')]//a[contains(@class, 'username')]//font"
            )?.textContent?.trim()
                ?: throw VripperException("Unable to find the poster for post #'${postEntity.vgPostId}'")


            val node: Node = XpathUtils.getAsNode(
                document, java.lang.String.format("//div[@id='post_message_%s']", postEntity.vgPostId)
            ) ?: throw VripperException("Unable to locate post content")
            val titles = findTitleInContent(node)
            val metadataEntity = MetadataEntity(postEntity.id, MetadataEntity.Data(postedBy, titles))
            dataAccessService.saveMetadata(metadataEntity)
            status = TaskStatus.SUCCESS
            result =
                "Fetched metadata for post #${postEntity.vgPostId}: posted by \"$postedBy\", ${titles.size} title(s)"
        } catch (e: Exception) {
            status = TaskStatus.FAILURE
            result = "Failed to fetch metadata for post #${postEntity.vgPostId}: ${e.message ?: e.toString()}"
            log.error("Failed to fetch metadata for post #${postEntity.vgPostId}", e)
        }
    }

    private fun findTitleInContent(node: Node): List<String> {
        val altTitle: MutableList<String> = mutableListOf()
        findTitle(node, altTitle, AtomicBoolean(true))
        return altTitle.stream().distinct().collect(Collectors.toList())
    }

    private fun findTitle(node: Node, altTitle: MutableList<String>, keepGoing: AtomicBoolean) {
        if (!keepGoing.get()) {
            return
        }
        if (node.nodeName == "a" || node.nodeName == "img") {
            keepGoing.set(false)
            return
        }
        if (node.nodeType == Node.ELEMENT_NODE) {
            for (i in 0 until node.childNodes.length) {
                val item = node.childNodes.item(i)
                findTitle(item, altTitle, keepGoing)
                if (!keepGoing.get()) {
                    return
                }
            }
        } else if (node.nodeType == Node.TEXT_NODE) {
            val text = node.textContent.trim()
            if (text.isNotBlank() && dictionary.stream().noneMatch { e ->
                    text.lowercase().contains(e.lowercase())
                }) {
                altTitle.add(text)
            }
        }
    }
}
