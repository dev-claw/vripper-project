package me.vripper

import me.vripper.listeners.AppManager
import org.koin.core.context.startKoin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

@SpringBootApplication
class VripperWebApplication

fun main(args: Array<String>) {
    startKoin {
        modules(coreModule)
    }
    AppManager.start()
    SpringApplicationBuilder(VripperWebApplication::class.java).listeners(AppListener()).run(*args)
}