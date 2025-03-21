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
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.message.BasicNameValuePair

internal class VGAuthService(
    private val cm: HTTPService,
    private val eventBus: EventBus,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log by LoggerDelegate()
    private val vgCookies: MutableList<Cookie> = mutableListOf()
    var loggedUser = ""
    private var authenticated = false

    fun init() {
        coroutineScope.launch {
            eventBus.events.filterIsInstance(SettingsUpdateEvent::class).collect {
                authenticate(it.settings)
            }
        }
    }

    private fun authenticate(settings: Settings) {
        if (!settings.viperSettings.login) {
            log.debug("Authentication option is disabled")
            authenticated = false
            loggedUser = ""
            synchronized(vgCookies) {
                vgCookies.clear()
            }
            eventBus.publishEvent(VGUserLoginEvent(loggedUser))
            return
        }
        val username = settings.viperSettings.username
        val password = settings.viperSettings.password
        if (username.isEmpty() || password.isEmpty()) {
            log.error("Cannot authenticate with ViperGirls credentials, username or password is empty")
            authenticated = false
            loggedUser = ""
            synchronized(vgCookies) {
                vgCookies.clear()
            }
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
        log.info("Authenticating: ${postAuth.uri}")
        try {
            val context = HttpClientContext.create().apply {
                cookieStore =
                    BasicCookieStore()
            }
            cm.client.execute(postAuth, context) { response ->
                if (response.code / 100 != 2) {
                    throw VripperException("Unexpected response code returned ${response.code}")
                }
                val responseBody = EntityUtils.toString(response.entity)
                log.debug("Authentication with ViperGirls response body:{}", responseBody)
            }

            val userIdCookie = context.cookieStore.cookies.find { it.name == "vg_userid" }
            val passwordCookie = context.cookieStore.cookies.find { it.name == "vg_password" }

            if (userIdCookie == null || passwordCookie == null) {
                log.error(
                    "Failed to authenticate user with {}, missing vg_userid/vg_password cookie",
                    settings.viperSettings.host
                )
                eventBus.publishEvent(VGUserLoginEvent(loggedUser))
                return
            }
            synchronized(vgCookies) {
                vgCookies.clear()
                vgCookies.add(BasicClientCookie(userIdCookie.name, userIdCookie.value).apply {
                    domain = userIdCookie.domain
                })
                vgCookies.add(BasicClientCookie(passwordCookie.name, passwordCookie.value).apply {
                    domain = passwordCookie.domain
                })
            }
        } catch (e: Exception) {
            log.error(
                "Failed to authenticate user with " + settings.viperSettings.host, e
            )
            authenticated = false
            loggedUser = ""
            eventBus.publishEvent(VGUserLoginEvent(loggedUser))
            return
        }
        authenticated = true
        loggedUser = username
        eventBus.publishEvent(VGUserLoginEvent(loggedUser))
    }

    fun leaveThanks(postEntity: PostEntity) {
        val context = createVgContext()
        taskRunner.submit(
            LeaveThanksTask(postEntity, authenticated, context)
        )
    }

    fun createVgContext(): HttpClientContext {
        val context = HttpClientContext().apply { cookieStore = BasicCookieStore() }
        synchronized(vgCookies) {
            if (authenticated && vgCookies.isNotEmpty()) {
                vgCookies.forEach { c -> context.cookieStore.addCookie(c) }
            }
        }
        return context
    }

    fun createClickContext(): HttpClientContext {
        val context = HttpClientContext().apply { cookieStore = BasicCookieStore() }
        synchronized(vgCookies) {
            if (authenticated && vgCookies.isNotEmpty()) {
                vgCookies.forEach { c ->
                    context.cookieStore.addCookie(
                        BasicClientCookie(
                            c.name,
                            c.value
                        ).apply { domain = "viper.click" })
                }
            }
        }
        return context
    }
}