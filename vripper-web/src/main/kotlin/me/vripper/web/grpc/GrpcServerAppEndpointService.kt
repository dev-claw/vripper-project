package me.vripper.web.grpc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.vripper.entities.ImageEntity
import me.vripper.model.*
import me.vripper.proto.*
import me.vripper.proto.EndpointServiceOuterClass.DownloadSpeed
import me.vripper.proto.EndpointServiceOuterClass.ErrorCount
import me.vripper.proto.ImageOuterClass.Image
import me.vripper.proto.ImagesOuterClass.Images
import me.vripper.proto.LogOuterClass.Log
import me.vripper.proto.ThreadsOuterClass.Threads
import me.vripper.services.IAppEndpointService
import me.vripper.services.download.MovePosition
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class GrpcServerAppEndpointService : EndpointServiceGrpcKt.EndpointServiceCoroutineImplBase(), KoinComponent {

    private val appEndpointService: IAppEndpointService by inject(named("localAppEndpointService"))

    override suspend fun scanLinks(request: EndpointServiceOuterClass.Links): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.scanLinks(request.links)
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override fun onNewPosts(request: EndpointServiceOuterClass.EmptyRequest): Flow<PostOuterClass.Post> =
        appEndpointService.onNewPosts().map(::mapper)


    override fun onUpdatePosts(request: EndpointServiceOuterClass.EmptyRequest): Flow<PostOuterClass.Post> =
        appEndpointService.onUpdatePosts().map(::mapper)


    override fun onDeletePosts(request: EndpointServiceOuterClass.EmptyRequest): Flow<EndpointServiceOuterClass.Id> =
        appEndpointService.onDeletePosts().map { EndpointServiceOuterClass.Id.newBuilder().setId(it).build() }


    override suspend fun findAllPosts(request: EndpointServiceOuterClass.EmptyRequest): PostsOuterClass.Posts =
        PostsOuterClass.Posts.newBuilder().addAllPosts(appEndpointService.findAllPosts().map(::mapper)).build()


    override suspend fun restartAll(request: EndpointServiceOuterClass.IdList): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.restartAll(request.idsList)
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun stopAll(request: EndpointServiceOuterClass.IdList): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.stopAll(request.idsList)
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun findPost(request: EndpointServiceOuterClass.Id): PostOuterClass.Post {
        return mapper(appEndpointService.findPost(request.id))
    }

    override suspend fun findImagesByPostId(request: EndpointServiceOuterClass.Id): Images =
        Images.newBuilder().addAllImages(appEndpointService.findImagesByPostEntityId(request.id).map(::mapper)).build()


    override fun onUpdateImages(request: EndpointServiceOuterClass.EmptyRequest): Flow<Image> =
        appEndpointService.onUpdateImages().map(::mapper)

    override fun onUpdateImagesByPostId(request: EndpointServiceOuterClass.Id): Flow<Image> =
        appEndpointService.onUpdateImagesByPostEntityId(request.id).map(::mapper)

    override fun onDownloadSpeed(request: EndpointServiceOuterClass.EmptyRequest): Flow<DownloadSpeed> =
        appEndpointService.onDownloadSpeed().map { DownloadSpeed.newBuilder().setSpeed(it.speed).build() }

    override fun onVGUserUpdate(request: EndpointServiceOuterClass.EmptyRequest): Flow<EndpointServiceOuterClass.VGUser> =
        appEndpointService.onVGUserUpdate().map { EndpointServiceOuterClass.VGUser.newBuilder().setUser(it).build() }

    override fun onQueueStateUpdate(request: EndpointServiceOuterClass.EmptyRequest): Flow<EndpointServiceOuterClass.QueueState> =
        appEndpointService.onQueueStateUpdate().map(::mapper)

    override suspend fun getQueueState(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.QueueState {
        return mapper(appEndpointService.getQueueState())
    }

    override fun onErrorCountUpdate(request: EndpointServiceOuterClass.EmptyRequest): Flow<ErrorCount> =
        appEndpointService.onErrorCountUpdate().map { ErrorCount.newBuilder().setCount(it.count).build() }

    override fun onTasksRunning(request: EndpointServiceOuterClass.EmptyRequest): Flow<EndpointServiceOuterClass.TasksRunning> =
        appEndpointService.onTasksRunning()
            .map { EndpointServiceOuterClass.TasksRunning.newBuilder().setRunning(it).build() }


    override fun onUpdateMetadata(request: EndpointServiceOuterClass.EmptyRequest): Flow<MetadataOuterClass.Metadata> {
        return appEndpointService.onUpdateMetadata().map { mapper(it) }
    }

    override suspend fun remove(request: EndpointServiceOuterClass.IdList): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.remove(request.idsList)
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun move(request: EndpointServiceOuterClass.MovePositionMessage): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.move(request.postEntityId, MovePosition.valueOf(request.position.name))
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun clearCompleted(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.IdList {
        return EndpointServiceOuterClass.IdList.newBuilder().addAllIds(appEndpointService.clearCompleted()).build()
    }

    override suspend fun rename(request: EndpointServiceOuterClass.Rename): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.rename(request.postId, request.name)
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun renameToFirst(request: EndpointServiceOuterClass.RenameToFirst): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.renameToFirst(request.postIdsList)
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override fun onStopped(request: EndpointServiceOuterClass.EmptyRequest): Flow<EndpointServiceOuterClass.Id> {
        return appEndpointService.onStopped().map { EndpointServiceOuterClass.Id.newBuilder().setId(it).build() }
    }

    override fun onNewLog(request: EndpointServiceOuterClass.EmptyRequest): Flow<Log> {
        return appEndpointService.onNewLog().map(::mapper)
    }

    override suspend fun initLogger(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.initLogger()
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override fun onNewThread(request: EndpointServiceOuterClass.EmptyRequest): Flow<ThreadOuterClass.Thread> {
        return appEndpointService.onNewThread().map(::mapper)
    }

    override fun onUpdateThread(request: EndpointServiceOuterClass.EmptyRequest): Flow<ThreadOuterClass.Thread> {
        return appEndpointService.onUpdateThread().map(::mapper)
    }

    override fun onDeleteThread(request: EndpointServiceOuterClass.EmptyRequest): Flow<EndpointServiceOuterClass.Id> {
        return appEndpointService.onDeleteThread().map { EndpointServiceOuterClass.Id.newBuilder().setId(it).build() }
    }

    override fun onClearThreads(request: EndpointServiceOuterClass.EmptyRequest): Flow<EndpointServiceOuterClass.EmptyResponse> {
        return appEndpointService.onClearThreads().map { EndpointServiceOuterClass.EmptyResponse.getDefaultInstance() }
    }

    override suspend fun findAllThreads(request: EndpointServiceOuterClass.EmptyRequest): Threads {
        return Threads.newBuilder().addAllThreads(appEndpointService.findAllThreads().map { mapper(it) }).build()
    }

    override suspend fun threadRemove(request: EndpointServiceOuterClass.IdList): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.threadRemove(request.idsList)
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun threadClear(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.threadClear()
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun grab(request: EndpointServiceOuterClass.Id): EndpointServiceOuterClass.PostSelectionList {
        return EndpointServiceOuterClass.PostSelectionList.newBuilder()
            .addAllPostSelectionList(appEndpointService.grab(request.id).map(::mapper)).build()
    }

    override suspend fun download(request: EndpointServiceOuterClass.ThreadPostIdList): EndpointServiceOuterClass.EmptyResponse {
        appEndpointService.download(request.threadPostIdListList.map {
            ThreadPostId(
                it.threadId, it.postId
            )
        })
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun getSettings(request: EndpointServiceOuterClass.EmptyRequest): SettingsOuterClass.Settings {
        return mapper(appEndpointService.getSettings())
    }

    override fun onUpdateSettings(request: EndpointServiceOuterClass.EmptyRequest): Flow<SettingsOuterClass.Settings> {
        return appEndpointService.onUpdateSettings().map(::mapper)
    }

    override suspend fun saveSettings(request: SettingsOuterClass.Settings): EndpointServiceOuterClass.EmptyResponse {
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
        return EndpointServiceOuterClass.EmptyResponse.getDefaultInstance()
    }

    override suspend fun getProxies(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.ProxyList {
        return EndpointServiceOuterClass.ProxyList.newBuilder().addAllProxies(appEndpointService.getProxies()).build()
    }

    override suspend fun loggedInUser(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.LoggedInUser =
        EndpointServiceOuterClass.LoggedInUser.newBuilder().setUser(appEndpointService.loggedInUser()).build()

    override suspend fun getVersion(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.Version =
        EndpointServiceOuterClass.Version.newBuilder().setVersion(appEndpointService.getVersion()).build()

    override suspend fun dbMigration(request: EndpointServiceOuterClass.EmptyRequest): EndpointServiceOuterClass.DBMigrationResponse =
        EndpointServiceOuterClass.DBMigrationResponse.newBuilder().setMessage(appEndpointService.dbMigration()).build()


    private fun mapper(queueState: QueueState): EndpointServiceOuterClass.QueueState {
        return with(
            EndpointServiceOuterClass.QueueState.newBuilder()
        ) {
            setRunning(queueState.running)
            setRemaining(queueState.remaining)
            addAllRank(queueState.rank.map { rank ->
                EndpointServiceOuterClass.Rank.newBuilder().setPostEntityId(rank.postEntityId).setRank(rank.rank)
                    .build()
            }).build()
        }
    }

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
            url = image.url
            thumbUrl = image.thumbUrl
            host = image.host.toInt()
            index = image.index
            postIdRef = image.postEntityId
            size = image.size
            downloaded = image.downloaded
            status = image.status.name
            filename = image.filename

            build()
        }
    }

    private fun mapper(metadata: Metadata): MetadataOuterClass.Metadata {
        return with(MetadataOuterClass.Metadata.newBuilder()) {
            postId = metadata.postIdRef
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

    private fun mapper(postSelection: PostSelection): EndpointServiceOuterClass.PostSelection {
        return with(EndpointServiceOuterClass.PostSelection.newBuilder()) {
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