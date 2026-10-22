package me.vripper.gui

import me.vripper.gui.services.GrpcEndpointService
import me.vripper.gui.services.IGrpcEndpointService
import me.vripper.services.IAppEndpointService
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val guiModule = module {
    single<GrpcEndpointService>(named("remoteAppEndpointService")) {
        GrpcEndpointService()
    } bind IAppEndpointService::class

    single<IGrpcEndpointService>(named("remoteAppEndpointService")) {
        get<GrpcEndpointService>(named("remoteAppEndpointService"))
    }
}

val modules = module {
    includes(me.vripper.coreModule, guiModule)
}