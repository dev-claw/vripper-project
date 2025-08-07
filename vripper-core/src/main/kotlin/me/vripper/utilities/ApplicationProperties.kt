package me.vripper.utilities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.vripper.exception.DownloadException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readLines

object ApplicationProperties {
    val VERSION: String =
        ApplicationProperties.javaClass.getResourceAsStream("/version")
            ?.use { inputStream -> inputStream.reader().use { it.readText() } } ?: "undefined"
    private const val BASE_DIR_NAME: String = "vripper"
    private val portable = System.getProperty("vripper.portable", "true").toBoolean()
    private val BASE_DIR: String = getBaseDir()
    val VRIPPER_DIR: Path = Path(BASE_DIR, BASE_DIR_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
    }
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"
    val USER_AGENT: String

    const val DEFAULT_IMX_SUBDOMAINS = "i.imx.to,i001.imx.to,i002.imx.to,i004.imx.to,i006.imx.to,i003.imx.to"
    val IMX_SUBDOMAINS: Set<String>

    @Serializable
    internal data class ReleaseResponse(@SerialName("tag_name") val tagName: String)

    init {
        Files.createDirectories(VRIPPER_DIR)
        System.setProperty("VRIPPER_DIR", VRIPPER_DIR.toRealPath().pathString)
        val userAgentOverride = VRIPPER_DIR.resolve("user-agent.txt")
        USER_AGENT = if (userAgentOverride.exists()) {
            userAgentOverride.readLines().firstOrNull() ?: DEFAULT_USER_AGENT
        } else {
            DEFAULT_USER_AGENT
        }

        val imxSubdomainsOverride = VRIPPER_DIR.resolve("imx-subdomains.txt")
        IMX_SUBDOMAINS = if (imxSubdomainsOverride.exists()) {
            imxSubdomainsOverride.readLines().firstOrNull() ?: DEFAULT_IMX_SUBDOMAINS
        } else {
            DEFAULT_IMX_SUBDOMAINS
        }.split(",").toSet()
    }

    fun latestVersion(): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/dev-claw/vripper-project/releases/latest")).build()
        return HttpClient.newHttpClient().use {
            val response = it.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() / 100 != 2) {
                throw DownloadException("Unexpected response code '${response.statusCode()}' for $request")
            }
            json.decodeFromString<ReleaseResponse>(response.body()).tagName
        }
    }

    private fun getBaseDir(): String {
        return if (portable) {
            System.getProperty("user.dir")
        } else {
            val os = System.getProperty("os.name")
            if (os.contains("Windows")) {
                System.getProperty("user.home")
            } else if (os.contains("Linux")) {
                "${System.getProperty("user.home")}/.config"
            } else if (os.contains("Mac")) {
                "${System.getProperty("user.home")}/.config"
            } else {
                System.getProperty("user.dir")
            }
        }
    }
}
