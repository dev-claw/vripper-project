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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.notExists

object Watcher : KoinComponent {
    private val localAppEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))
    private val remoteAppEndpointService: IAppEndpointService by inject(named("remoteAppEndpointService"))
    private val log by LoggerDelegate()
    private val ready = CountDownLatch(1)

    @Volatile
    private var key: WatchKey? = null

    @Volatile
    var stopped = false

    fun init() {
        Thread.ofVirtual().name("vripper-watcher").start(Runnable {
            watch()
        })

        Runtime.getRuntime().addShutdownHook(Thread {
            destroy()
        })
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) {
                log.warn("File watcher did not become ready within 5 seconds; continuing startup")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun destroy() {
        stopped = true
    }

    private fun watch() {
        try {
            val watchService: WatchService = FileSystems.getDefault().newWatchService()
            val path = VRIPPER_DIR.resolve("posts")
            if (path.notExists()) {
                path.createDirectories()
            }
            key = path.register(
                watchService, StandardWatchEventKinds.ENTRY_CREATE
            )
            ready.countDown()

            while (!stopped) {
                val key: WatchKey? = watchService.take()
                if (key == null) {
                    break
                }

                key.pollEvents().forEach {
                    runBlocking {
                        log.info("Handling thread ${it.context()}")
                        val service =
                            if (WidgetSettings.loadSettings().localSession) localAppEndpointService
                            else remoteAppEndpointService
                        val host = GuiErrorReporter.call("watcher.settings") {
                            service.getSettings().viperSettings.host
                        }.getOrNull()
                        if (host != null) {
                            GuiErrorReporter.call("watcher.scan") {
                                service.scanLinks("$host/threads/${it.context()}")
                            }
                        }
                        path.resolve(it.context().toString()).deleteIfExists()
                    }
                }
                key.reset()
            }
        } catch (t: Throwable) {
            log.error("File watcher stopped unexpectedly", t)
            stopped = true
        } finally {
            ready.countDown()
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