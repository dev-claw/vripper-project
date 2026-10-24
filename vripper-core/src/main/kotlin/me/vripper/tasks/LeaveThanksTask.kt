package me.vripper.tasks

import me.vripper.entities.PostEntity
import me.vripper.exception.VripperException
import me.vripper.services.HTTPService
import me.vripper.services.SettingsService
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.RequestLimit
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.message.BasicNameValuePair
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class LeaveThanksTask(
    private val postEntity: PostEntity,
    private val authenticated: Boolean,
    private val context: HttpClientContext
) : KoinComponent, ITask {
    private val log by LoggerDelegate()
    private val cm: HTTPService by inject()
    private val settingsService: SettingsService by inject()
    private var status = TaskStatus.WAITING
    private var result = ""

    override fun getName() = "Leaving thanks"

    override fun getCategory() = TaskCategory.THANKS

    override fun getDescription() = "Leaving thanks for post #${postEntity.vgPostId}"

    override fun getResult() = result

    override fun getStatus() = status

    override suspend fun run() {
        status = TaskStatus.RUNNING
        if (!settingsService.settings.viperSettings.login || !authenticated) {
            status = TaskStatus.WARNING
            result = "Skipped: not authenticated with ViperGirls"
            return
        }
        if (!settingsService.settings.viperSettings.thanks) {
            status = TaskStatus.WARNING
            result = "Skipped: leaving thanks is disabled"
            return
        }
        try {
            val postThanks = HttpPost("${settingsService.settings.viperSettings.host}/post_thanks.php").also {
                it.entity = UrlEncodedFormEntity(
                    listOf(
                        BasicNameValuePair("do", "post_thanks_add"),
                        BasicNameValuePair("using_ajax", "1"),
                        BasicNameValuePair("p", postEntity.vgPostId.toString()),
                        BasicNameValuePair("securitytoken", postEntity.token)
                    )
                )
                it.setAbsoluteRequestUri(true)
            }
            RequestLimit.getPermit(1)
            log.info("Posting {}", postThanks)
            cm.client.execute(postThanks, context) { response ->
                if (response.code / 100 != 2) {
                    throw VripperException("Unexpected response code '${response.code}' for $postThanks")
                }
            }
            status = TaskStatus.SUCCESS
            result = "Left thanks for post #${postEntity.vgPostId}"
        } catch (e: Exception) {
            status = TaskStatus.FAILURE
            result = "Failed to leave thanks for post #${postEntity.vgPostId}: ${e.message ?: e.toString()}"
            log.error("Failed to leave a thanks for $postEntity", e)
        }
    }
}
