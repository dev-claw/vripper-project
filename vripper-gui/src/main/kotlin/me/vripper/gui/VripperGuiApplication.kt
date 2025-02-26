package me.vripper.gui

import atlantafx.base.theme.CupertinoDark
import atlantafx.base.theme.CupertinoLight
import javafx.application.Application
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.stage.WindowEvent
import kotlinx.coroutines.*
import me.vripper.gui.components.views.LoadingView
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.utils.Watcher
import me.vripper.gui.utils.WidgetSettings
import me.vripper.utilities.AppLock
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import me.vripper.utilities.LoggerDelegate
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import tornadofx.App
import tornadofx.DIContainer
import tornadofx.FX
import kotlin.reflect.KClass
import kotlin.system.exitProcess


class VripperGuiApplication : App(
    LoadingView::class
) {
    private val log by LoggerDelegate()
    private var initialized = false
    private val widgetsController: WidgetsController by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        APP_INSTANCE = this
    }

    override fun start(stage: Stage) {
        Thread.setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler { t, e ->
            log.error("Thread $t threw an exception", e)
        })
        if (widgetsController.currentSettings.darkMode) {
            setUserAgentStylesheet(CupertinoDark().userAgentStylesheet)
        } else {
            setUserAgentStylesheet(CupertinoLight().userAgentStylesheet)
        }
        with(stage) {
            width = widgetsController.currentSettings.width
            height = widgetsController.currentSettings.height
            minWidth = 100.0
            minHeight = 100.0
            icons.addAll(
                listOf(
                    Image("icons/16x16.png"),
                    Image("icons/32x32.png"),
                    Image("icons/48x48.png"),
                    Image("icons/64x64.png"),
                    Image("icons/128x128.png"),
                    Image("icons/256x256.png"),
                    Image("icons/512x512.png"),
                    Image("icons/1024x1024.png")
                )
            )
        }
        coroutineScope.launch {
            while (isActive) {
                if (!stage.isMaximized) {
                    if (widgetsController.currentSettings.width != stage.width || widgetsController.currentSettings.height != stage.height) {
                        widgetsController.currentSettings.width = stage.width
                        widgetsController.currentSettings.height = stage.height
                        WidgetSettings.update(widgetsController.currentSettings)
                    }
                }
                delay(1_000)
            }
        }
        stage.addEventFilter(WindowEvent.WINDOW_SHOWN) {
            startKoin {
                modules(modules)
            }
            FX.dicontainer =
                object : DIContainer {
                    override fun <T : Any> getInstance(type: KClass<T>): T =
                        GlobalContext.get().get(type)

                    override fun <T : Any> getInstance(type: KClass<T>, name: String): T =
                        GlobalContext.get().get(type, named(name))

                }
            coroutineScope.launch {
                GuiEventBus.publishEvent(GuiEventBus.ApplicationInitialized(parameters.raw))
                initialized = true
            }
        }
        super.start(stage)
    }

    override fun stop() {
        coroutineScope.cancel()
        super.stop()
        exitProcess(0)
    }

    companion object {
        lateinit var APP_INSTANCE: Application
    }
}

fun main(args: Array<String>) {
    System.setProperty("prism.lcdtext", "false")
    val pattern = "(vr:t=)(\\d+)".toPattern()
    val threadId = if (args.isNotEmpty()) {
        val matcher = pattern.matcher(args[0])
        if (!matcher.matches()) {
            System.err.println("Illegal argument ${args[0]}")
            exitProcess(-1)
        }
        matcher.group(2)
    } else {
        null
    }
    val haveTheLock = AppLock.exclusiveLock()
    if (!haveTheLock) {
        if (threadId == null) {
            System.err.println("Another instance is already running in $VRIPPER_DIR")
            exitProcess(-1)
        }
        Watcher.notify(threadId)
        exitProcess(0)
    }
    Watcher.init()
    Application.launch(
        VripperGuiApplication::class.java,
        *if (threadId != null) arrayOf(threadId) else emptyArray<String>()
    )
}
