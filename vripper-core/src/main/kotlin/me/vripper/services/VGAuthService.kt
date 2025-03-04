package me.vripper.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import me.vripper.entities.PostEntity
import me.vripper.event.EventBus
import me.vripper.event.SettingsUpdateEvent
import me.vripper.event.VGUserLoginEvent
import me.vripper.exception.VripperException
import me.vripper.model.Settings
import me.vripper.tasks.LeaveThanksTask
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.taskRunner
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.cookie.BasicCookieStore
import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.message.BasicNameValuePair

internal class VGAuthService(
    private val cm: HTTPService,
    private val settingsService: SettingsService,
    private val eventBus: EventBus
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log by LoggerDelegate()
    val context: HttpClientContext = HttpClientContext.create()
    var loggedUser = ""

    private var authenticated = false

    init {
        context.cookieStore = BasicCookieStore()
        coroutineScope.launch {
            eventBus.events.filterIsInstance(SettingsUpdateEvent::class).collect {
                authenticate(it.settings)
            }
        }
        authenticate(settingsService.settings)
    }

    private fun authenticate(settings: Settings) {
        authenticated = false
        if (!settings.viperSettings.login) {
            log.debug("Authentication option is disabled")
            context.cookieStore.clear()
            loggedUser = ""
            eventBus.publishEvent(VGUserLoginEvent(loggedUser))
            return
        }
        val username = settings.viperSettings.username
        val password = settings.viperSettings.password
        if (username.isEmpty() || password.isEmpty()) {
            log.error("Cannot authenticate with ViperGirls credentials, username or password is empty")
            context.cookieStore.clear()
            loggedUser = ""
            eventBus.publishEvent(VGUserLoginEvent(loggedUser))
            return
        }
        val postAuth = HttpPost(settings.viperSettings.host + "/login.php?do=login").also {
            it.entity = UrlEncodedFormEntity(
                listOf(
                    BasicNameValuePair("vb_login_username", username),
                    BasicNameValuePair("cookieuser", "1"),
                    BasicNameValuePair("do", "login"),
                    BasicNameValuePair("vb_login_md5password", password)
                )
            )
        }

        try {
            cm.client.execute(postAuth, context) { response ->
                if (response.code / 100 != 2) {
                    throw VripperException("Unexpected response code returned ${response.code}")
                }
                val responseBody = EntityUtils.toString(response.entity)
                log.debug("Authentication with ViperGirls response body:{}", responseBody)
            }
            if (context.cookieStore.cookies.stream().map { obj: Cookie -> obj.name }
                    .noneMatch { e: String -> e == "vg_userid" }) {
                log.error(
                    "Failed to authenticate user with {}, missing vg_userid cookie",
                    settings.viperSettings.host
                )
                return
            }
        } catch (e: Exception) {
            context.cookieStore.clear()
            loggedUser = ""
            eventBus.publishEvent(VGUserLoginEvent(loggedUser))
            log.error(
                "Failed to authenticate user with " + settings.viperSettings.host, e
            )
            return
        }
        authenticated = true
        loggedUser = username
        eventBus.publishEvent(VGUserLoginEvent(loggedUser))
    }

    fun leaveThanks(postEntity: PostEntity) {
        taskRunner.submit(
            LeaveThanksTask(postEntity, authenticated, context)
        )
    }
}