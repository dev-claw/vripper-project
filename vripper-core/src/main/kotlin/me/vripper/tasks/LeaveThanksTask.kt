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
) : KoinComponent, Runnable {
    private val log by LoggerDelegate()
    private val cm: HTTPService by inject()
    private val settingsService: SettingsService by inject()

    override fun run() {
        try {
            Tasks.increment()
            if (!settingsService.settings.viperSettings.login || !authenticated) {
                return
            }
            if (!settingsService.settings.viperSettings.thanks) {
                return
            }
            val postThanks = HttpPost("${settingsService.settings.viperSettings.host}/post_thanks.php").also {
                it.entity = UrlEncodedFormEntity(
                    listOf(
                        BasicNameValuePair("do", "post_thanks_add"),
                        BasicNameValuePair("using_ajax", "1"),
                        BasicNameValuePair("p", postEntity.postId.toString()),
                        BasicNameValuePair("securitytoken", postEntity.token)
                    )
                )
            }
            RequestLimit.getPermit(1)
            log.info("Posting {}", postThanks.uri)
            cm.client.execute(postThanks, context) { response ->
                if (response.code / 100 != 2) {
                    throw VripperException("Unexpected response code '${response.code}' for $postThanks")
                }
            }
        } catch (e: Exception) {
            log.error("Failed to leave a thanks for $postEntity", e)
        } finally {
            Tasks.decrement()
        }
    }
}