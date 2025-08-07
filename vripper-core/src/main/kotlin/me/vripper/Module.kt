package me.vripper

import me.vripper.data.repositories.ImageRepository
import me.vripper.data.repositories.MetadataRepository
import me.vripper.data.repositories.PostRepository
import me.vripper.data.repositories.ThreadRepository
import me.vripper.data.repositories.impl.ImageRepositoryImpl
import me.vripper.data.repositories.impl.MetadataRepositoryImpl
import me.vripper.data.repositories.impl.PostRepositoryImpl
import me.vripper.data.repositories.impl.ThreadRepositoryImpl
import me.vripper.event.EventBus
import me.vripper.host.*
import me.vripper.services.*
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val coreModule = module {
    single<EventBus> {
        EventBus
    }
    single<SettingsService> {
        SettingsService(get())
    }
    single<LogService> {
        LogService(get())
    }
    single<ImageRepository> {
        ImageRepositoryImpl()
    }
    single<PostRepository> {
        PostRepositoryImpl()
    }
    single<MetadataRepository> {
        MetadataRepositoryImpl()
    }
    single<ThreadRepository> {
        ThreadRepositoryImpl()
    }
    single<DataTransaction> {
        DataTransaction(get(), get(), get(), get(), get(), get())
    }
    single<RetryPolicyService> {
        RetryPolicyService()
    }
    single<HTTPService> {
        HTTPService()
    }
    single<VGAuthService> {
        VGAuthService(get(), get())
    }
    single<ThreadCacheService> {
        ThreadCacheService(get())
    }
    single<DownloadService> {
        DownloadService(get(), get(), get(), get())
    }
    single<DownloadSpeedService> {
        DownloadSpeedService(get())
    }

    single<AppEndpointService> {
        AppEndpointService(get(), get(), get(), get(), get(), get())
    }

    single((named("localAppEndpointService"))) {
        AppEndpointService(get(), get(), get(), get(), get(), get())
    } bind IAppEndpointService::class

    single<MetadataService> {
        MetadataService(get(), get())
    }
    single {
        AcidimgHost(get())
    } bind Host::class
    single {
        DPicMeHost()
    } bind Host::class
    single {
        ImageBamHost()
    } bind Host::class
    single {
        ImageTwistHost()
    } bind Host::class
    single {
        ImageVenueHost()
    } bind Host::class
    single {
        ImageZillaHost()
    } bind Host::class
    single {
        ImgboxHost()
    } bind Host::class
    single {
        ImgSpiceHost()
    } bind Host::class
    single {
        ImxHost()
    } bind Host::class
    single {
        PimpandhostHost()
    } bind Host::class
    single {
        PixhostHost()
    } bind Host::class
    single {
        PixRouteHost()
    } bind Host::class
    single {
        PixxxelsHost()
    } bind Host::class
    single {
        PostImgHost()
    } bind Host::class
    single {
        TurboImageHost()
    } bind Host::class
    single {
        ViprImHost()
    } bind Host::class
}