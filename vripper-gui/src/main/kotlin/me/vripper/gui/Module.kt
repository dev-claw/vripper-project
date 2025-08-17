package me.vripper.gui

import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.services.GrpcEndpointService
import me.vripper.services.IAppEndpointService
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val guiModule = module {
    single((named("remoteAppEndpointService"))) {
        GrpcEndpointService()
    } bind IAppEndpointService::class
    single<WidgetsController> {
        WidgetsController()
    }
}

val modules = module {
    includes(me.vripper.coreModule, guiModule)
}