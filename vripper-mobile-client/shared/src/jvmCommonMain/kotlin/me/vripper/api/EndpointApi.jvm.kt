package me.vripper.api

import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcServerStreamingCall
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import me.vripper.data.DownloadTask
import me.vripper.data.ForumThread
import me.vripper.data.Image
import me.vripper.data.LogEntry
import me.vripper.data.MovePosition
import me.vripper.data.Post
import me.vripper.data.PostMetadata
import me.vripper.data.QueueState
import me.vripper.data.RemoteImageChunk
import me.vripper.data.Settings
import me.vripper.data.ThreadPostRef
import me.vripper.data.ThreadSelection
import me.vripper.data.toDomain
import me.vripper.data.toProto
import me.vripper.proto.DownloadRequest
import me.vripper.proto.EmptyRequest
import me.vripper.proto.EndpointServiceClient
import me.vripper.proto.Id
import me.vripper.proto.IdList
import me.vripper.proto.Links
import me.vripper.proto.MovePositionEnum
import me.vripper.proto.MovePositionMessage
import me.vripper.proto.Rename
import me.vripper.proto.RenameToFirst
import me.vripper.proto.ThreadPostId
import me.vripper.proto.ThreadPostIdList
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol

private const val MAX_CONCURRENT_REQUESTS = 256

actual fun createEndpointApi(host: String, port: Int, passPhrase: String): EndpointApi {
    // Wire dispatches server-streaming calls through OkHttp's async dispatcher, whose
    // default per-host limit is 5. The app keeps far more streams open, so unary calls
    // (actions, settings, logging) would otherwise be queued behind them forever.
    val dispatcher = Dispatcher().apply {
        maxRequests = MAX_CONCURRENT_REQUESTS
        maxRequestsPerHost = MAX_CONCURRENT_REQUESTS
    }
    val httpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(E2eEncryptionInterceptor(passPhrase, createAesGcm()))
        .build()

    val hostName = host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .trimEnd('/')

    val grpcClient = GrpcClient.Builder()
        .client(httpClient)
        .baseUrl("http://$hostName:$port")
        .minMessageToCompress(Long.MAX_VALUE)
        .build()

    return WireEndpointApi(
        client = grpcClient.create(),
        httpClient = httpClient,
    )
}

private class WireEndpointApi(
    private val client: EndpointServiceClient,
    private val httpClient: OkHttpClient,
) : EndpointApi {
    override suspend fun getVersion(): String =
        client.getVersion().execute(EmptyRequest()).version

    override suspend fun loggedInUser(): String =
        client.loggedInUser().execute(EmptyRequest()).user

    override suspend fun findAllPosts(): List<Post> =
        client.findAllPosts().execute(EmptyRequest()).posts.map { it.toDomain() }

    override suspend fun findPost(postId: Long): Post =
        client.findPost().execute(Id(id = postId)).toDomain()

    override suspend fun getQueueState(): QueueState =
        client.getQueueState().execute(EmptyRequest()).toDomain()

    override fun onNewPosts(): Flow<Post> = stream(client::onNewPosts) { it.toDomain() }

    override fun onUpdatePosts(): Flow<Post> = stream(client::onUpdatePosts) { it.toDomain() }

    override fun onDeletePosts(): Flow<Long> = stream(client::onDeletePosts) { it.id }

    override fun onUpdateMetadata(): Flow<PostMetadata> =
        stream(client::onUpdateMetadata) { it.toDomain() }

    override fun onQueueStateUpdate(): Flow<QueueState> =
        stream(client::onQueueStateUpdate) { it.toDomain() }

    override suspend fun scanLinks(links: String) {
        client.scanLinks().execute(Links(links = links, origin = "PASTED"))
    }

    override suspend fun startAllPosts() {
        client.restartAll().execute(IdList())
    }

    override suspend fun stopAllPosts() {
        client.stopAll().execute(IdList())
    }

    override suspend fun startPosts(ids: List<Long>) {
        client.restartAll().execute(IdList(ids = ids))
    }

    override suspend fun stopPosts(ids: List<Long>) {
        client.stopAll().execute(IdList(ids = ids))
    }

    override suspend fun removePosts(ids: List<Long>) {
        client.remove().execute(IdList(ids = ids))
    }

    override suspend fun clearCompletedPosts(): List<Long> =
        client.clearCompleted().execute(EmptyRequest()).ids

    override suspend fun renamePost(id: Long, name: String) {
        client.rename().execute(Rename(postId = id, name = name))
    }

    override suspend fun renameToFirst(postIds: List<Long>) {
        client.renameToFirst().execute(RenameToFirst(postIds = postIds))
    }

    override suspend fun movePost(id: Long, position: MovePosition) {
        client.move().execute(
            MovePositionMessage(
                postEntityId = id,
                position = MovePositionEnum.valueOf(position.name),
            ),
        )
    }

    override suspend fun findImages(postId: Long): List<Image> =
        client.findImagesByPostId().execute(Id(id = postId)).images.map { it.toDomain() }

    override fun onUpdateImagesByPost(postId: Long): Flow<Image> =
        stream(Id(id = postId), client::onUpdateImagesByPostId) { it.toDomain() }

    override fun onUpdateImages(): Flow<Image> =
        stream(client::onUpdateImages) { it.toDomain() }

    override fun downloadImage(imageId: Long): Flow<RemoteImageChunk> =
        stream(DownloadRequest(imageId = imageId), client::downloadImage) { it.toDomain() }

    override suspend fun findAllThreads(): List<ForumThread> =
        client.findAllThreads().execute(EmptyRequest()).threads.map { it.toDomain() }

    override fun onNewThread(): Flow<ForumThread> =
        stream(client::onNewThread) { it.toDomain() }

    override fun onUpdateThread(): Flow<ForumThread> =
        stream(client::onUpdateThread) { it.toDomain() }

    override fun onDeleteThread(): Flow<Long> = stream(client::onDeleteThread) { it.id }

    override fun onClearThreads(): Flow<Unit> = stream(client::onClearThreads) { }

    override suspend fun removeThreads(ids: List<Long>) {
        client.threadRemove().execute(IdList(ids = ids))
    }

    override suspend fun clearThreads() {
        client.threadClear().execute(EmptyRequest())
    }

    override suspend fun grabThread(threadId: Long): List<ThreadSelection> =
        client.grab().execute(Id(id = threadId)).postSelectionList.map { it.toDomain() }

    override suspend fun downloadThreadPosts(posts: List<ThreadPostRef>) {
        client.download().execute(
            ThreadPostIdList(
                threadPostIdList = posts.map { ThreadPostId(threadId = it.threadId, postId = it.postId) },
            ),
        )
    }

    override suspend fun findAllTasks(): List<DownloadTask> =
        client.findAllTasks().execute(EmptyRequest()).tasks.map { it.toDomain() }

    override fun onNewTask(): Flow<DownloadTask> =
        stream(client::onNewTask) { it.toDomain() }

    override fun onUpdateTask(): Flow<DownloadTask> =
        stream(client::onUpdateTask) { it.toDomain() }

    override fun onClearTasks(): Flow<Unit> = stream(client::onClearTasks) { }

    override suspend fun clearTasks() {
        client.clearTasks().execute(EmptyRequest())
    }

    override fun onNewLog(): Flow<LogEntry> =
        stream(client::onNewLog) { it.toDomain() }

    override suspend fun initLogger() {
        client.initLogger().execute(EmptyRequest())
    }

    override suspend fun getSettings(): Settings =
        client.getSettings().execute(EmptyRequest()).toDomain()

    override suspend fun saveSettings(settings: Settings) {
        client.saveSettings().execute(settings.toProto())
    }

    override suspend fun getProxies(): List<String> =
        client.getProxies().execute(EmptyRequest()).proxies

    override fun onUpdateSettings(): Flow<Settings> =
        stream(client::onUpdateSettings) { it.toDomain() }

    override fun onDownloadSpeed(): Flow<Long> =
        stream(client::onDownloadSpeed) { it.speed }

    override fun onVGUserUpdate(): Flow<String> =
        stream(client::onVGUserUpdate) { it.user }

    override fun onErrorCountUpdate(): Flow<Int> =
        stream(client::onErrorCountUpdate) { it.count }

    override fun onTasksRunning(): Flow<Boolean> =
        stream(client::onTasksRunning) { it.running }

    private fun <T : Any, R : Any> stream(
        callFactory: () -> GrpcServerStreamingCall<EmptyRequest, T>,
        map: (T) -> R,
    ): Flow<R> = stream(EmptyRequest(), callFactory, map)

    private fun <T : Any, R : Any, Req : Any> stream(
        request: Req,
        callFactory: () -> GrpcServerStreamingCall<Req, T>,
        map: (T) -> R,
    ): Flow<R> = channelFlow {
        val channel = callFactory().executeIn(this, request)
        try {
            for (message in channel) send(map(message))
        } finally {
            channel.cancel()
        }
    }

    override fun close() {
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }

}
