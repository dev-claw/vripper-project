package me.vripper.web.grpc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.vripper.entities.ImageEntity
import me.vripper.model.*
import me.vripper.proto.*
import me.vripper.proto.EndpointServiceOuterClass.*
import me.vripper.proto.EndpointServiceOuterClass.DownloadSpeed
import me.vripper.proto.EndpointServiceOuterClass.ErrorCount
import me.vripper.proto.EndpointServiceOuterClass.PostSelection
import me.vripper.proto.EndpointServiceOuterClass.QueueState
import me.vripper.proto.ImageOuterClass.Image
import me.vripper.proto.ImagesOuterClass.Images
import me.vripper.proto.LogOuterClass.Log
import me.vripper.proto.ThreadsOuterClass.Threads
import me.vripper.services.IAppEndpointService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class GrpcServerAppEndpointService : EndpointServiceGrpcKt.EndpointServiceCoroutineImplBase(), KoinComponent {

    private val appEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))

    override suspend fun scanLinks(request: Links): EmptyResponse {
        appEndpointService.scanLinks(request.links)
        return EmptyResponse.getDefaultInstance()
    }

    override fun onNewPosts(request: EmptyRequest): Flow<PostOuterClass.Post> =
        appEndpointService.onNewPosts().map(::mapper)


    override fun onUpdatePosts(request: EmptyRequest): Flow<PostOuterClass.Post> =
        appEndpointService.onUpdatePosts().map(::mapper)


    override fun onDeletePosts(request: EmptyRequest): Flow<Id> =
        appEndpointService.onDeletePosts().map { Id.newBuilder().setId(it).build() }


    override suspend fun findAllPosts(request: EmptyRequest): PostsOuterClass.Posts =
        PostsOuterClass.Posts.newBuilder().addAllPosts(appEndpointService.findAllPosts().map(::mapper)).build()


    override suspend fun restartAll(request: IdList): EmptyResponse {
        appEndpointService.restartAll(request.idsList)
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun stopAll(request: IdList): EmptyResponse {
        appEndpointService.stopAll(request.idsList)
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun findPost(request: Id): PostOuterClass.Post {
        return mapper(appEndpointService.findPost(request.id))
    }

    override suspend fun findImagesByPostId(request: Id): Images =
        Images.newBuilder().addAllImages(appEndpointService.findImagesByPostId(request.id).map(::mapper)).build()


    override fun onUpdateImages(request: EmptyRequest): Flow<Image> = appEndpointService.onUpdateImages().map(::mapper)
    override fun onUpdateImagesByPostId(request: Id): Flow<Image> =
        appEndpointService.onUpdateImagesByPostId(request.id).map(::mapper)

    override fun onDownloadSpeed(request: EmptyRequest): Flow<DownloadSpeed> =
        appEndpointService.onDownloadSpeed().map { DownloadSpeed.newBuilder().setSpeed(it.speed).build() }

    override fun onVGUserUpdate(request: EmptyRequest): Flow<VGUser> =
        appEndpointService.onVGUserUpdate().map { VGUser.newBuilder().setUser(it).build() }

    override fun onQueueStateUpdate(request: EmptyRequest): Flow<QueueState> = appEndpointService.onQueueStateUpdate()
        .map { QueueState.newBuilder().setRunning(it.running).setRemaining(it.remaining).build() }

    override fun onErrorCountUpdate(request: EmptyRequest): Flow<ErrorCount> =
        appEndpointService.onErrorCountUpdate().map { ErrorCount.newBuilder().setCount(it.count).build() }

    override fun onTasksRunning(request: EmptyRequest): Flow<TasksRunning> =
        appEndpointService.onTasksRunning().map { TasksRunning.newBuilder().setRunning(it).build() }


    override fun onUpdateMetadata(request: EmptyRequest): Flow<MetadataOuterClass.Metadata> {
        return appEndpointService.onUpdateMetadata().map { mapper(it) }
    }

    override suspend fun remove(request: IdList): EmptyResponse {
        appEndpointService.remove(request.idsList)
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun clearCompleted(request: EmptyRequest): IdList {
        return IdList.newBuilder().addAllIds(appEndpointService.clearCompleted()).build()
    }

    override suspend fun rename(request: Rename): EmptyResponse {
        appEndpointService.rename(request.postId, request.name)
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun renameToFirst(request: RenameToFirst): EmptyResponse {
        appEndpointService.renameToFirst(request.postIdsList)
        return EmptyResponse.getDefaultInstance()
    }

    override fun onStopped(request: EmptyRequest): Flow<Id> {
        return appEndpointService.onStopped().map { Id.newBuilder().setId(it).build() }
    }

    override fun onNewLog(request: EmptyRequest): Flow<Log> {
        return appEndpointService.onNewLog().map(::mapper)
    }

    override suspend fun initLogger(request: EmptyRequest): EmptyResponse {
        appEndpointService.initLogger()
        return EmptyResponse.getDefaultInstance()
    }

    override fun onNewThread(request: EmptyRequest): Flow<ThreadOuterClass.Thread> {
        return appEndpointService.onNewThread().map(::mapper)
    }

    override fun onUpdateThread(request: EmptyRequest): Flow<ThreadOuterClass.Thread> {
        return appEndpointService.onUpdateThread().map(::mapper)
    }

    override fun onDeleteThread(request: EmptyRequest): Flow<Id> {
        return appEndpointService.onDeleteThread().map { Id.newBuilder().setId(it).build() }
    }

    override fun onClearThreads(request: EmptyRequest): Flow<EmptyResponse> {
        return appEndpointService.onClearThreads().map { EmptyResponse.getDefaultInstance() }
    }

    override suspend fun findAllThreads(request: EmptyRequest): Threads {
        return Threads.newBuilder().addAllThreads(appEndpointService.findAllThreads().map { mapper(it) }).build()
    }

    override suspend fun threadRemove(request: IdList): EmptyResponse {
        appEndpointService.threadRemove(request.idsList)
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun threadClear(request: EmptyRequest): EmptyResponse {
        appEndpointService.threadClear()
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun grab(request: Id): PostSelectionList {
        return PostSelectionList.newBuilder().addAllPostSelectionList(appEndpointService.grab(request.id).map(::mapper))
            .build()
    }

    override suspend fun download(request: ThreadPostIdList): EmptyResponse {
        appEndpointService.download(request.threadPostIdListList.map {
            me.vripper.model.ThreadPostId(
                it.threadId, it.postId
            )
        })
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun getSettings(request: EmptyRequest): SettingsOuterClass.Settings {
        return mapper(appEndpointService.getSettings())
    }

    override fun onUpdateSettings(request: EmptyRequest): Flow<SettingsOuterClass.Settings> {
        return appEndpointService.onUpdateSettings().map(::mapper)
    }

    override suspend fun saveSettings(request: SettingsOuterClass.Settings): EmptyResponse {
        appEndpointService.saveSettings(
            Settings(
                connectionSettings = ConnectionSettings(
                    maxConcurrentPerHost = request.connectionSettings.maxConcurrentPerHost,
                    maxGlobalConcurrent = request.connectionSettings.maxGlobalConcurrent,
                    timeout = request.connectionSettings.timeout,
                    maxAttempts = request.connectionSettings.maxAttempts,
                ), downloadSettings = DownloadSettings(
                    downloadPath = request.downloadSettings.downloadPath,
                    autoStart = request.downloadSettings.autoStart,
                    autoQueueThreshold = request.downloadSettings.autoQueueThreshold,
                    forceOrder = request.downloadSettings.forceOrder,
                    forumSubDirectory = request.downloadSettings.forumSubDirectory,
                    threadSubLocation = request.downloadSettings.threadSubLocation,
                    clearCompleted = request.downloadSettings.clearCompleted,
                    appendPostId = request.downloadSettings.appendPostId,
                ), viperSettings = ViperSettings(
                    login = request.viperSettings.login,
                    username = request.viperSettings.username,
                    password = request.viperSettings.password,
                    thanks = request.viperSettings.thanks,
                    host = request.viperSettings.host,
                    requestLimit = request.viperSettings.requestLimit,
                    fetchMetadata = request.viperSettings.fetchMetadata,
                ), systemSettings = SystemSettings(
                    tempPath = request.systemSettings.tempPath,
                    enableClipboardMonitoring = request.systemSettings.enableClipboardMonitoring,
                    clipboardPollingRate = request.systemSettings.clipboardPollingRate,
                    maxEventLog = request.systemSettings.maxEventLog,
                )
            )
        )
        return EmptyResponse.getDefaultInstance()
    }

    override suspend fun getProxies(request: EmptyRequest): ProxyList {
        return ProxyList.newBuilder().addAllProxies(appEndpointService.getProxies()).build()
    }

    override suspend fun loggedInUser(request: EmptyRequest): LoggedInUser =
        LoggedInUser.newBuilder().setUser(appEndpointService.loggedInUser()).build()

    override suspend fun getVersion(request: EmptyRequest): Version =
        Version.newBuilder().setVersion(appEndpointService.getVersion()).build()

    override suspend fun dbMigration(request: EmptyRequest): DBMigrationResponse =
        DBMigrationResponse.newBuilder().setMessage(appEndpointService.dbMigration()).build()


    private fun mapper(settings: Settings): SettingsOuterClass.Settings {
        val viperSettings = with(SettingsOuterClass.ViperSettings.newBuilder()) {
            login = settings.viperSettings.login
            username = settings.viperSettings.username
            password = settings.viperSettings.password
            thanks = settings.viperSettings.thanks
            host = settings.viperSettings.host
            requestLimit = settings.viperSettings.requestLimit
            fetchMetadata = settings.viperSettings.fetchMetadata
            build()
        }

        val downloadSettings = with(SettingsOuterClass.DownloadSettings.newBuilder()) {
            downloadPath = settings.downloadSettings.downloadPath
            autoStart = settings.downloadSettings.autoStart
            autoQueueThreshold = settings.downloadSettings.autoQueueThreshold
            forceOrder = settings.downloadSettings.forceOrder
            forumSubDirectory = settings.downloadSettings.forumSubDirectory
            threadSubLocation = settings.downloadSettings.threadSubLocation
            clearCompleted = settings.downloadSettings.clearCompleted
            appendPostId = settings.downloadSettings.appendPostId
            build()
        }

        val connectionSettings = with(SettingsOuterClass.ConnectionSettings.newBuilder()) {
            maxConcurrentPerHost = settings.connectionSettings.maxConcurrentPerHost
            maxGlobalConcurrent = settings.connectionSettings.maxGlobalConcurrent
            timeout = settings.connectionSettings.timeout
            maxAttempts = settings.connectionSettings.maxAttempts
            build()
        }

        val systemSettings = with(SettingsOuterClass.SystemSettings.newBuilder()) {
            tempPath = settings.systemSettings.tempPath
            enableClipboardMonitoring = settings.systemSettings.enableClipboardMonitoring
            clipboardPollingRate = settings.systemSettings.clipboardPollingRate
            maxEventLog = settings.systemSettings.maxEventLog
            build()
        }

        return with(SettingsOuterClass.Settings.newBuilder()) {
            this.connectionSettings = connectionSettings
            this.downloadSettings = downloadSettings
            this.viperSettings = viperSettings
            this.systemSettings = systemSettings
            build()
        }
    }

    private fun mapper(post: Post): PostOuterClass.Post {
        return with(PostOuterClass.Post.newBuilder()) {
            id = post.id
            postTitle = post.postTitle
            threadTitle = post.threadTitle
            forum = post.forum
            url = post.url
            token = post.token
            postId = post.postId
            threadId = post.threadId
            total = post.total
            addAllHosts(post.hosts)
            downloadDirectory = post.downloadDirectory
            addedOn = post.addedOn.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            folderName = post.folderName
            status = post.status.name
            done = post.done
            rank = post.rank
            size = post.size
            downloaded = post.downloaded
            addAllPreviews(post.previews)
            this.postedBy = post.postedBy
            addAllResolvedNames(post.resolvedNames)

            build()
        }
    }

    private fun mapper(image: ImageEntity): Image {
        return with(Image.newBuilder()) {
            id = image.id
            postId = image.postId
            url = image.url
            thumbUrl = image.thumbUrl
            host = image.host.toInt()
            index = image.index
            postIdRef = image.postIdRef
            size = image.size
            downloaded = image.downloaded
            status = image.status.name
            filename = image.filename

            build()
        }
    }

    private fun mapper(metadata: Metadata): MetadataOuterClass.Metadata {
        return with(MetadataOuterClass.Metadata.newBuilder()) {
            postId = metadata.postId
            postedBy = metadata.data.postedBy
            addAllResolvedNames(metadata.data.resolvedNames)

            build()
        }
    }

    private fun mapper(log: LogEntry): Log {
        return with(Log.newBuilder()) {
            timestamp = log.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            threadName = log.threadName
            loggerName = log.loggerName
            levelString = log.levelString
            formattedMessage = log.formattedMessage
            throwable = log.throwable
            build()
        }
    }

    private fun mapper(thread: Thread): ThreadOuterClass.Thread {
        return with(ThreadOuterClass.Thread.newBuilder()) {
            id = thread.id
            title = thread.title
            link = thread.link
            threadId = thread.threadId
            total = thread.total
            build()
        }
    }

    private fun mapper(postSelection: me.vripper.model.PostSelection): PostSelection {
        return with(PostSelection.newBuilder()) {
            threadId = postSelection.threadId
            threadTitle = postSelection.threadTitle
            postId = postSelection.postId
            number = postSelection.number
            title = postSelection.title
            imageCount = postSelection.imageCount
            url = postSelection.url
            hosts = postSelection.hosts
            forum = postSelection.forum
            addAllPreviews(postSelection.previews)
            build()
        }
    }
}