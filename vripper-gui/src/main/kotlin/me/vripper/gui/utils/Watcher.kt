package me.vripper.gui.utils

import kotlinx.coroutines.runBlocking
import me.vripper.services.IAppEndpointService
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import me.vripper.utilities.LoggerDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.notExists

object Watcher : KoinComponent {
    private val localAppEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))
    private val remoteAppEndpointService: IAppEndpointService by inject(named("remoteAppEndpointService"))
    private val log by LoggerDelegate()
    private var key: WatchKey? = null
    var stopped = false

    fun init() {
        Thread.ofVirtual().start(Runnable {
            watch()
        })

        Runtime.getRuntime().addShutdownHook(Thread {
            destroy()
        })
        while (key == null && !stopped && !Thread.interrupted()) {
            Thread.sleep(100)
        }
    }

    private fun destroy() {
        stopped = true
    }

    private fun watch() {
        val watchService: WatchService = FileSystems.getDefault().newWatchService()
        val path = VRIPPER_DIR.resolve("posts")
        if (path.notExists()) {
            path.createDirectories()
        }
        key = path.register(
            watchService, StandardWatchEventKinds.ENTRY_CREATE
        )

        while (!stopped) {
            val key: WatchKey? = watchService.take()
            if (key == null) {
                break
            }

            key.pollEvents().forEach {
                runBlocking {
                    log.info("Handling thread ${it.context()}")
                    if (WidgetSettings.loadSettings().localSession) {
                        val link = localAppEndpointService.getSettings().viperSettings.host + "/threads/${it.context()}"
                        runCatching { localAppEndpointService.scanLinks(link) }
                    } else {
                        val link =
                            remoteAppEndpointService.getSettings().viperSettings.host + "/threads/${it.context()}"
                        runCatching { remoteAppEndpointService.scanLinks(link) }
                    }
                    path.resolve(it.context().toString()).deleteIfExists()
                }
            }
            key.reset()
        }
    }

    fun notify(threadId: String) {
        val path = VRIPPER_DIR.resolve("posts").resolve(threadId)
        if (path.notExists()) {
            path.createFile()
        }
        log.info("Notifying running instance to handle thread $threadId")
    }
}