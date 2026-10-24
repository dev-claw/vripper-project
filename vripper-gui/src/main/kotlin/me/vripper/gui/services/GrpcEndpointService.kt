package me.vripper.gui.services

import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.vripper.entities.MetadataEntity
import me.vripper.entities.Status
import me.vripper.entities.TaskEntity
import me.vripper.model.*
import me.vripper.model.DownloadRequest
import me.vripper.model.DownloadSpeed
import me.vripper.model.ErrorCount
import me.vripper.model.ImageChunk
import me.vripper.model.PostSelection
import me.vripper.model.QueueState
import me.vripper.model.Rank
import me.vripper.model.ThreadPostId
import me.vripper.proto.*
import me.vripper.proto.EndpointServiceOuterClass.*
import me.vripper.proto.LogOuterClass.Log
import me.vripper.services.IPostCommandEndpoint
import me.vripper.services.download.MovePosition
import me.vripper.tasks.TaskCategory
import me.vripper.tasks.TaskStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class GrpcEndpointService : IGrpcEndpointService {

    @Volatile
    private var channel: ManagedChannel? = null

    @Volatile
    private var endpointServiceCoroutineStub: EndpointServiceGrpcKt.EndpointServiceCoroutineStub? = null

    private val stub: EndpointServiceGrpcKt.EndpointServiceCoroutineStub
        get() = endpointServiceCoroutineStub
            ?: throw IllegalStateException("gRPC endpoint is not connected")

    private companion object {
        const val MAX_INBOUND_MESSAGE_SIZE = 64 * 1024 * 1024
        const val MIN_SUPPORTED_VERSION = "6.6.0"
    }

    override suspend fun scanLinks(postLinks: String, origin: IPostCommandEndpoint.ScanOrigin) {
        stub.scanLinks(Links.newBuilder().setLinks(postLinks).setOrigin(origin.name).build())
    }

    override suspend fun restartAll(postEntityIds: List<Long>) {
        stub.restartAll(IdList.newBuilder().addAllIds(postEntityIds).build())
    }

    override suspend fun remove(postEntityIds: List<Long>) {
        stub.remove(IdList.newBuilder().addAllIds(postEntityIds).build())
    }

    override suspend fun stopAll(postEntityIds: List<Long>) {
        stub.stopAll(IdList.newBuilder().addAllIds(postEntityIds).build())
    }

    override suspend fun clearCompleted(): List<Long> {
        return stub.clearCompleted(EmptyRequest.getDefaultInstance()).idsList
    }

    override suspend fun findPost(postEntityId: Long): Post =
        mapper(stub.findPost(Id.newBuilder().setId(postEntityId).build()))

    override suspend fun findAllPosts(): List<Post> =
        stub.findAllPosts(EmptyRequest.getDefaultInstance()).postsList.map(::mapper)

    override suspend fun rename(postEntityId: Long, newName: String) {
        stub.rename(
            Rename.newBuilder().setPostId(postEntityId).setName(newName).build()
        )
    }

    override suspend fun renameToFirst(postEntityIds: List<Long>) {
        stub.renameToFirst(RenameToFirst.newBuilder().addAllPostIds(postEntityIds).build())
    }

    override fun onNewPosts(): Flow<Post> =
        stub.onNewPosts(EmptyRequest.getDefaultInstance()).map { mapper(it) }


    override fun onUpdatePosts(): Flow<Post> =
        stub.onUpdatePosts(EmptyRequest.getDefaultInstance()).map {
            mapper(it)
        }


    override fun onDeletePosts() = stub.onDeletePosts(EmptyRequest.getDefaultInstance()).map {
        it.id
    }


    override fun onUpdateMetadata(): Flow<Metadata> =
        stub.onUpdateMetadata(EmptyRequest.getDefaultInstance()).map { mapper(it) }


    override suspend fun findImagesByPostEntityId(postEntityId: Long): List<Image> =
        stub.findImagesByPostId(
            Id.newBuilder().setId(postEntityId).build()
        ).imagesList.map(::mapper)


    override fun onUpdateImagesByPostEntityId(postEntityId: Long): Flow<Image> =
        stub.onUpdateImagesByPostId(Id.newBuilder().setId(postEntityId).build()).map(::mapper)

    override fun onUpdateImages(): Flow<Image> =
        stub.onUpdateImages(EmptyRequest.getDefaultInstance()).map(::mapper)


    override fun onStopped(): Flow<Long> =
        stub.onStopped(EmptyRequest.getDefaultInstance()).map { it.id }


    override fun onNewLog(): Flow<LogEntry> =
        stub.onNewLog(EmptyRequest.getDefaultInstance()).map { mapper(it) }

    override suspend fun initLogger() {
        stub.initLogger(EmptyRequest.getDefaultInstance())
    }

    override fun onNewThread() =
        stub.onNewThread(EmptyRequest.getDefaultInstance()).map { mapper(it) }

    override fun onUpdateThread() =
        stub.onUpdateThread(EmptyRequest.getDefaultInstance()).map { mapper(it) }

    override fun onDeleteThread() =
        stub.onDeleteThread(EmptyRequest.getDefaultInstance()).map { it.id }

    override fun onClearThreads() =
        stub.onClearThreads(EmptyRequest.getDefaultInstance()).map { }

    override suspend fun findAllThreads(): List<Thread> =
        stub.findAllThreads(EmptyRequest.getDefaultInstance()).threadsList.map(::mapper)


    override suspend fun threadRemove(threadIdList: List<Long>) {
        stub.threadRemove(IdList.newBuilder().addAllIds(threadIdList).build())
    }

    override suspend fun move(postEntityId: Long, position: MovePosition) {
        stub.move(
            MovePositionMessage.newBuilder().setPostEntityId(postEntityId)
                .setPosition(MovePositionEnum.valueOf(position.name)).build()
        )
    }

    override suspend fun threadClear() {
        stub.threadClear(EmptyRequest.getDefaultInstance())
    }

    override suspend fun grab(threadId: Long): List<PostSelection> =
        stub.grab(Id.newBuilder().setId(threadId).build()).postSelectionListList.map {
            mapper(
                it
            )
        }


    override suspend fun download(posts: List<ThreadPostId>) {
        stub.download(
            ThreadPostIdList.newBuilder().addAllThreadPostIdList(posts.map {
                EndpointServiceOuterClass.ThreadPostId.newBuilder().setThreadId(it.threadId).setPostId(it.postId)
                    .build()
            }).build()
        )
    }

    override fun onDownloadSpeed(): Flow<DownloadSpeed> =
        stub.onDownloadSpeed(EmptyRequest.getDefaultInstance())
            .map { DownloadSpeed(it.speed) }

    override fun onVGUserUpdate(): Flow<String> =
        stub.onVGUserUpdate(EmptyRequest.getDefaultInstance()).map { it.user }

    override fun onQueueStateUpdate(): Flow<QueueState> =
        stub.onQueueStateUpdate(EmptyRequest.getDefaultInstance()).map(::mapper)

    override suspend fun getQueueState(): QueueState =
        mapper(stub.getQueueState(EmptyRequest.getDefaultInstance()))


    override fun onErrorCountUpdate(): Flow<ErrorCount> =
        stub.onErrorCountUpdate(EmptyRequest.getDefaultInstance())
            .map { ErrorCount(it.count) }

    override fun onTasksRunning(): Flow<Boolean> =
        stub.onTasksRunning(EmptyRequest.getDefaultInstance()).map { it.running }

    override suspend fun findAllTasks(): List<TaskEntity> =
        stub.findAllTasks(EmptyRequest.getDefaultInstance()).tasksList.map(::mapper)

    override fun onNewTask(): Flow<TaskEntity> =
        stub.onNewTask(EmptyRequest.getDefaultInstance()).map(::mapper)

    override fun onUpdateTask(): Flow<TaskEntity> =
        stub.onUpdateTask(EmptyRequest.getDefaultInstance()).map(::mapper)

    override suspend fun clearTasks() {
        stub.clearTasks(EmptyRequest.getDefaultInstance())
    }

    override fun onClearTasks(): Flow<Unit> =
        stub.onClearTasks(EmptyRequest.getDefaultInstance()).map { }

    override suspend fun getSettings(): Settings =
        mapper(stub.getSettings(EmptyRequest.getDefaultInstance()))

    override suspend fun saveSettings(settings: Settings) {
        stub.saveSettings(settings.toProto())
    }

    override suspend fun getProxies(): List<String> =
        stub.getProxies(EmptyRequest.getDefaultInstance()).proxiesList

    override fun onUpdateSettings(): Flow<Settings> =
        stub.onUpdateSettings(EmptyRequest.getDefaultInstance()).map(::mapper)

    override suspend fun loggedInUser(): String =
        stub.loggedInUser(EmptyRequest.getDefaultInstance()).user

    override suspend fun getVersion(): String =
        stub.getVersion(EmptyRequest.getDefaultInstance()).version

    override suspend fun getServerInfo(): ServerInfo =
        mapper(endpointServiceCoroutineStub!!.getServerInfo(EmptyRequest.getDefaultInstance()))

    override suspend fun dbMigration(): String =
        stub.dbMigration(EmptyRequest.getDefaultInstance()).message

    override fun downloadImage(downloadRequest: DownloadRequest): Flow<ImageChunk> =
        stub.downloadImage(
            EndpointServiceOuterClass
                .DownloadRequest
                .newBuilder()
                .setImageId(downloadRequest.imageId).build()
        ).map { ImageChunk(it.missing, it.imageId, it.offset, it.data.toByteArray(), it.isLast) }

    private fun mapper(info: ServerInfoResponse): ServerInfo =
        ServerInfo(
            version = info.version,
            applicationFolder = info.applicationFolder,
            databasePath = info.databasePath,
            databaseExists = info.databaseExists,
            databaseSize = info.databaseSize,
            logPath = info.logPath,
            logExists = info.logExists,
            logSize = info.logSize,
            settingsPath = info.settingsPath,
            settingsExists = info.settingsExists,
            settingsSize = info.settingsSize,
            downloadPath = info.downloadPath,
            tempPath = info.tempPath,
            operatingSystem = info.operatingSystem,
            javaVersion = info.javaVersion,
            javaVendor = info.javaVendor,
            uptimeMillis = info.uptimeMillis,
            threadCount = info.threadCount,
            heapUsed = info.heapUsed,
            heapCommitted = info.heapCommitted,
            heapMax = info.heapMax,
            processors = info.processors,
            gcCount = info.gcCount,
            gcTimeMillis = info.gcTimeMillis,
        )

    private fun mapper(queueState: EndpointServiceOuterClass.QueueState) =
        QueueState(queueState.running, queueState.remaining, queueState.rankList.map {
            Rank(it.postEntityId, it.rank)
        })

    private fun mapper(settings: SettingsOuterClass.Settings): Settings = Settings(
        viperSettings = ViperSettings(
            login = settings.viperSettings.login,
            username = settings.viperSettings.username,
            password = settings.viperSettings.password,
            thanks = settings.viperSettings.thanks,
            host = settings.viperSettings.host,
            requestLimit = settings.viperSettings.requestLimit,
            fetchMetadata = settings.viperSettings.fetchMetadata,
        ),
        downloadSettings = DownloadSettings(
            downloadPath = settings.downloadSettings.downloadPath,
            autoStart = settings.downloadSettings.autoStart,
            autoQueueThreshold = settings.downloadSettings.autoQueueThreshold,
            forceOrder = settings.downloadSettings.forceOrder,
            forumSubDirectory = settings.downloadSettings.forumSubDirectory,
            threadSubLocation = settings.downloadSettings.threadSubLocation,
            clearCompleted = settings.downloadSettings.clearCompleted,
            appendPostId = settings.downloadSettings.appendPostId,
        ),
        connectionSettings = ConnectionSettings(
            maxConcurrentPerHost = settings.connectionSettings.maxConcurrentPerHost,
            maxGlobalConcurrent = settings.connectionSettings.maxGlobalConcurrent,
            timeout = settings.connectionSettings.timeout,
            maxAttempts = settings.connectionSettings.maxAttempts,
        ),
        systemSettings = SystemSettings(
            tempPath = settings.systemSettings.tempPath,
            enableClipboardMonitoring = settings.systemSettings.enableClipboardMonitoring,
            clipboardPollingRate = settings.systemSettings.clipboardPollingRate,
            maxEventLog = settings.systemSettings.maxEventLog,
        ),
        hostSettings = settings.hostSettingsMap.entries.associate {
            val host = HostName.valueOf(it.key)
            val hostSettings = it.value.settingsMap.entries.associate { hostSettingsEntry ->
                val settingKey = HostSettingKey.valueOf(hostSettingsEntry.key)
                settingKey to hostSettingsEntry.value
            }
            host to hostSettings
        }
    )


    private fun Settings.toProto(): SettingsOuterClass.Settings {
        val viperSettings = with(SettingsOuterClass.ViperSettings.newBuilder()) {
            login = this@toProto.viperSettings.login
            username = this@toProto.viperSettings.username
            password = this@toProto.viperSettings.password
            thanks = this@toProto.viperSettings.thanks
            host = this@toProto.viperSettings.host
            requestLimit = this@toProto.viperSettings.requestLimit
            fetchMetadata = this@toProto.viperSettings.fetchMetadata
            build()
        }

        val downloadSettings = with(SettingsOuterClass.DownloadSettings.newBuilder()) {
            downloadPath = this@toProto.downloadSettings.downloadPath
            autoStart = this@toProto.downloadSettings.autoStart
            autoQueueThreshold = this@toProto.downloadSettings.autoQueueThreshold
            forceOrder = this@toProto.downloadSettings.forceOrder
            forumSubDirectory = this@toProto.downloadSettings.forumSubDirectory
            threadSubLocation = this@toProto.downloadSettings.threadSubLocation
            clearCompleted = this@toProto.downloadSettings.clearCompleted
            appendPostId = this@toProto.downloadSettings.appendPostId
            build()
        }

        val connectionSettings = with(SettingsOuterClass.ConnectionSettings.newBuilder()) {
            maxConcurrentPerHost = this@toProto.connectionSettings.maxConcurrentPerHost
            maxGlobalConcurrent = this@toProto.connectionSettings.maxGlobalConcurrent
            timeout = this@toProto.connectionSettings.timeout
            maxAttempts = this@toProto.connectionSettings.maxAttempts
            build()
        }

        val systemSettings = with(SettingsOuterClass.SystemSettings.newBuilder()) {
            tempPath = this@toProto.systemSettings.tempPath
            enableClipboardMonitoring = this@toProto.systemSettings.enableClipboardMonitoring
            clipboardPollingRate = this@toProto.systemSettings.clipboardPollingRate
            maxEventLog = this@toProto.systemSettings.maxEventLog
            build()
        }

        return with(SettingsOuterClass.Settings.newBuilder()) {
            this.connectionSettings = connectionSettings
            this.downloadSettings = downloadSettings
            this.viperSettings = viperSettings
            this.systemSettings = systemSettings
            this@toProto.hostSettings.forEach { (hostKey, hostSettings) ->
                val hostSetting = with(SettingsOuterClass.HostSettingsMap.newBuilder()) {
                    hostSettings.forEach { (settingKey, settingValue) ->
                        putSettings(settingKey.name, settingValue)
                    }
                    build()
                }
                putHostSettings(hostKey.name, hostSetting)
            }
            build()
        }
    }

    private fun mapper(postEntity: PostOuterClass.Post): Post {
        return Post(
            id = postEntity.id,
            postTitle = postEntity.postTitle,
            threadTitle = postEntity.threadTitle,
            forum = postEntity.forum,
            url = postEntity.url,
            token = postEntity.token,
            postId = postEntity.postId,
            threadId = postEntity.threadId,
            total = postEntity.total,
            hosts = postEntity.hostsList.toSet(),
            downloadDirectory = postEntity.downloadDirectory,
            addedOn = LocalDateTime.parse(postEntity.addedOn, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            folderName = postEntity.folderName,
            status = Status.valueOf(postEntity.status),
            done = postEntity.done,
            size = postEntity.size,
            downloaded = postEntity.downloaded,
            previews = postEntity.previewsList.toList(),
            postedBy = postEntity.postedBy,
            resolvedNames = postEntity.resolvedNamesList.toList(),
        )
    }

    private fun mapper(image: ImageOuterClass.Image): Image {
        return Image(
            id = image.id,
            url = image.url,
            thumbUrl = image.thumbUrl,
            host = image.host.toByte(),
            index = image.index,
            postEntityId = image.postIdRef,
            size = image.size,
            downloaded = image.downloaded,
            status = Status.valueOf(image.status),
            filename = image.filename,
        )
    }

    private fun mapper(metadata: MetadataOuterClass.Metadata): Metadata {
        return Metadata(
            metadata.postId, MetadataEntity.Data(metadata.postedBy, metadata.resolvedNamesList)
        )
    }

    private fun mapper(logEntry: Log): LogEntry {
        return LogEntry(
            logEntry.sequence,
            logEntry.timestamp,
            logEntry.threadName,
            logEntry.loggerName,
            logEntry.levelString,
            logEntry.formattedMessage,
            logEntry.throwable,
        )
    }

    private fun mapper(task: Task): TaskEntity {
        return TaskEntity(
            id = task.id,
            name = task.name,
            taskCategory = TaskCategory.valueOf(task.category),
            description = task.description,
            status = TaskStatus.valueOf(task.status),
            result = task.result,
            createdAt = LocalDateTime.parse(task.createdAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            updatedAt = LocalDateTime.parse(task.updatedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        )
    }

    private fun mapper(thread: ThreadOuterClass.Thread): Thread {
        return Thread(
            thread.id,
            thread.title,
            thread.link,
            thread.threadId,
            thread.total,
        )
    }

    private fun mapper(postSelection: EndpointServiceOuterClass.PostSelection): PostSelection {
        return PostSelection(
            postSelection.threadId,
            postSelection.threadTitle,
            postSelection.postId,
            postSelection.number,
            postSelection.title,
            postSelection.imageCount,
            postSelection.url,
            postSelection.hosts,
            postSelection.forum,
            postSelection.previewsList
        )
    }

    @Synchronized
    override fun connect(host: String, port: Int, passPhrase: String) {
        disconnect()
        val newChannel = ManagedChannelBuilder.forAddress(host, port)
            .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
            .usePlaintext()
            .intercept(ClientE2eEncryptingInterceptor(passPhrase))
            .build()
        channel = newChannel
        endpointServiceCoroutineStub = EndpointServiceGrpcKt.EndpointServiceCoroutineStub(newChannel)
    }

    @Synchronized
    override fun disconnect() {
        val currentChannel = channel
        channel = null
        endpointServiceCoroutineStub = null
        try {
            currentChannel?.shutdownNow()
        } catch (_: Exception) {

        }
    }

    override suspend fun versionCheck(): Boolean? {
        val result = runCatching {
            isAtLeastWithAlphaBeta(getVersion(), MIN_SUPPORTED_VERSION)
        }
        return result.getOrNull()
    }

    fun isAtLeastWithAlphaBeta(actual: String, min: String): Boolean {
        val (actualCore, actualSuffix) = parseVersion(actual) ?: return false
        val (minCore, minSuffix) = parseVersion(min) ?: return false

        val maxLen = maxOf(actualCore.size, minCore.size)
        for (i in 0 until maxLen) {
            val a = actualCore.getOrElse(i) { 0 }
            val m = minCore.getOrElse(i) { 0 }
            if (a != m) return a > m
        }

        return when {
            actualSuffix == null -> true
            minSuffix == null -> false
            else -> actualSuffix.compareTo(minSuffix) >= 0
        }
    }

    private fun parseVersion(version: String): Pair<List<Int>, String?>? {
        val core = version.substringBefore('-').trim()
        if (core.isEmpty()) return null
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        val suffix = version.substringAfter('-', "").trim().ifEmpty { null }
        return parts to suffix
    }

    override fun connectionState(): String = channel?.getState(false)?.name ?: ConnectivityState.SHUTDOWN.name
}