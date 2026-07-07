package me.vripper.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.sample
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.vripper.entities.*
import me.vripper.event.*
import me.vripper.exception.PostParseException
import me.vripper.model.*
import me.vripper.services.download.DownloadService
import me.vripper.services.download.MovePosition
import me.vripper.services.download.QueueManager
import me.vripper.tasks.QueueItemsTask
import me.vripper.tasks.RenameTask
import me.vripper.tasks.ScanLinksTask
import me.vripper.utilities.ApplicationProperties
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.extractBaseUrl
import org.h2.jdbc.JdbcSQLNonTransientConnectionException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Duration
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull
import java.nio.file.Path as NioPath

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class AppEndpointService(
    private val downloadService: DownloadService,
    private val queueManager: QueueManager,
    private val dataAccessService: DataAccessService,
    private val threadCacheService: ThreadCacheService,
    private val settingsService: SettingsService,
    private val vgAuthService: VGAuthService,
    private val logService: LogService,
    private val taskService: TaskService,
) : IAppEndpointService {

    private val log by LoggerDelegate()
    private val lock = Mutex()

    override suspend fun scanLinks(postLinks: String, origin: IPostCommandEndpoint.ScanOrigin) {
        taskService.submit(ScanLinksTask(postLinks, origin))
    }

    override suspend fun restartAll(postEntityIds: List<Long>) {
        lock.withLock {
            val posts = postEntityIds.mapNotNull { postEntityId ->
                if (dataAccessService.existsAsync(postEntityId)) {
                    dataAccessService.findPostByEntityIdAsync(postEntityId)
                } else {
                    null
                }
            }
            downloadService.restartAll(posts)
        }
    }

    override suspend fun download(posts: List<ThreadPostId>) {
        posts.map {
            val thread = dataAccessService.findThreadByThreadIdAsync(it.threadId).getOrNull()
                ?: throw PostParseException("Could not find thread with id ${it.threadId}")
            PostIdentifier(thread.link.extractBaseUrl(), it.threadId, it.postId)
        }.also {
            taskService.submit(QueueItemsTask(it))
        }
    }

    override suspend fun stopAll(postEntityIds: List<Long>) {
        lock.withLock {
            downloadService.stop(postEntityIds)
        }
    }

    override suspend fun remove(postEntityIds: List<Long>) {
        lock.withLock {
            downloadService.stop(postEntityIds)
            dataAccessService.removeAllAsync(postEntityIds)
        }
    }

    override suspend fun clearCompleted(): List<Long> {
        lock.withLock {
            return dataAccessService.clearCompletedAsync()
        }
    }

    override suspend fun grab(threadId: Long): List<PostSelection> {
        lock.withLock {
            return try {
                val thread = dataAccessService.findThreadByThreadIdAsync(threadId).orElseThrow {
                    PostParseException(
                        String.format(
                            "Unable to find links for threadId = %s", threadId
                        )
                    )
                }
                val threadLookupResult = threadCacheService[thread.threadId]
                if (threadLookupResult.error.isNotBlank()) {
                    throw PostParseException("Error occurred for threadId = $threadId, ${threadLookupResult.error}")
                }
                threadLookupResult.postItemList.map { postItem ->
                    PostSelection(
                        postItem.threadId,
                        postItem.threadTitle,
                        postItem.postId,
                        postItem.number,
                        postItem.title,
                        postItem.imageCount,
                        postItem.url,
                        postItem.hosts.joinToString(", ") { "${it.first} (${it.second})" },
                        postItem.forum,
                        postItem.imageItemList.map { it.thumbLink })
                }.ifEmpty {
                    throw PostParseException(
                        "Nothing found for threadId = $threadId"
                    )
                }
            } catch (e: Exception) {
                log.error(e.message)
                throw e
            }
        }
    }

    override suspend fun threadRemove(threadIdList: List<Long>) {
        lock.withLock {
            threadIdList.forEach {
                dataAccessService.removeThreadAsync(it)
            }
        }
    }

    override suspend fun threadClear() {
        lock.withLock {
            dataAccessService.clearQueueLinksAsync()
        }
    }

    override suspend fun renameToFirst(postEntityIds: List<Long>) {
        postEntityIds.forEach { postEntityId ->
            val metadata = dataAccessService.findMetadataByPostEntityIdAsync(postEntityId).orElse(null)
            metadata?.data?.resolvedNames?.firstOrNull()?.let { rename(postEntityId, it) }
        }
    }

    override suspend fun rename(postEntityId: Long, newName: String) {
        taskService.submit(RenameTask(postEntityId, newName))
    }

    override fun onNewPosts(): Flow<Post> =
        EventBus.events.filterIsInstance(PostCreateEvent::class)
            .map { it.postEntities.map { post -> mapper(post) } }
            .flatMapConcat { it.asFlow() }


    override fun onUpdatePosts(): Flow<Post> =
        EventBus.events.filterIsInstance(PostUpdateEvent::class)
            .map { it.postEntities.map { post -> mapper(post) } }
            .flatMapConcat { it.asFlow() }


    override fun onDeletePosts(): Flow<Long> =
        EventBus.events.filterIsInstance(PostDeleteEvent::class).flatMapConcat { it.postEntityIds.asFlow() }


    override fun onUpdateMetadata(): Flow<Metadata> =
        EventBus.events.filterIsInstance(MetadataUpdateEvent::class).map { it.metadataEntity }


    override suspend fun findAllPosts(): List<Post> {
        val snapshot = dataAccessService.findAllPostsSnapshotAsync()
        val imagesByPost = snapshot.images.groupBy { it.postEntityId }
        val metadataByPost = snapshot.metadata.associateBy { it.postIdRef }
        return snapshot.posts.map { postEntity ->
            mapper(postEntity, imagesByPost[postEntity.id].orEmpty(), metadataByPost[postEntity.id])
        }
    }

    private suspend fun mapper(postEntity: PostEntity): Post {
        val images = dataAccessService.findImagesByPostEntityIdAsync(postEntity.id)
        val metadata = dataAccessService.findMetadataByPostEntityIdAsync(postEntity.id).orElse(null)
        return mapper(postEntity, images, metadata)
    }

    private fun mapper(postEntity: PostEntity, images: List<ImageEntity>, metadata: MetadataEntity?): Post {
        return Post(
            postEntity.id,
            postEntity.postTitle,
            postEntity.threadTitle,
            postEntity.forum,
            postEntity.url,
            postEntity.token,
            postEntity.vgPostId,
            postEntity.vgThreadId,
            postEntity.total,
            postEntity.hosts,
            postEntity.downloadDirectory,
            postEntity.addedOn,
            postEntity.folderName,
            postEntity.status,
            postEntity.done,
            postEntity.size,
            postEntity.downloaded,
            images.map { it.thumbUrl },
            metadata?.data?.postedBy ?: "",
            metadata?.data?.resolvedNames ?: emptyList(),
        )
    }

    override suspend fun findPost(postEntityId: Long): Post {
        val snapshot = dataAccessService.findPostSnapshotAsync(postEntityId)
        return mapper(snapshot.post, snapshot.images, snapshot.metadata)
    }

    override suspend fun findImagesByPostEntityId(postEntityId: Long): List<Image> {
        return dataAccessService.findImagesByPostEntityIdAsync(postEntityId)
    }

    override fun onUpdateImagesByPostEntityId(postEntityId: Long): Flow<Image> =
        EventBus.events.filterIsInstance(ImageEvent::class).map {
            it.imageEntities.filter { imageEntity: Image -> imageEntity.postEntityId == postEntityId }
        }.filter { it.isNotEmpty() }.flatMapConcat { it.asFlow() }

    override fun onUpdateImages(): Flow<Image> =
        EventBus.events.filterIsInstance(ImageEvent::class).flatMapConcat { it.imageEntities.asFlow() }

    override fun onStopped(): Flow<Long> =
        EventBus.events.filterIsInstance(StoppedEvent::class).flatMapConcat { it.postIds.asFlow() }

    override fun onNewLog() = EventBus.events.filterIsInstance(LogCreateEvent::class).map { it.logEntryEntity }

    override suspend fun initLogger() = logService.init()

    override fun onNewThread(): Flow<Thread> =
        EventBus.events.filterIsInstance(ThreadCreateEvent::class).map { it.threadEntity }


    override fun onUpdateThread(): Flow<Thread> =
        EventBus.events.filterIsInstance(ThreadUpdateEvent::class).map { it.threadEntity }


    override fun onDeleteThread(): Flow<Long> =
        EventBus.events.filterIsInstance(ThreadDeleteEvent::class).map { it.threadId }


    override fun onClearThreads(): Flow<Unit> = EventBus.events.filterIsInstance(ThreadClearEvent::class).map { }


    override suspend fun findAllThreads(): List<Thread> {
        return dataAccessService.findAllThreadsAsync()
    }

    override fun onDownloadSpeed(): Flow<DownloadSpeed> =
        EventBus.events.filterIsInstance(DownloadSpeedEvent::class).map { it.downloadSpeed }

    override fun onVGUserUpdate(): Flow<String> =
        EventBus.events.filterIsInstance(VGUserLoginEvent::class).map { it.username }

    override fun onQueueStateUpdate(): Flow<QueueState> =
        EventBus.events.filterIsInstance(QueueStateEvent::class).map { it.queueState }

    override suspend fun getQueueState(): QueueState {
        return queueManager.getQueueState()
    }

    override fun onErrorCountUpdate(): Flow<ErrorCount> =
        EventBus.events.filterIsInstance(ErrorCountEvent::class).map { it.errorCount }

    override fun onTasksRunning(): Flow<Boolean> =
        EventBus.events.filterIsInstance(LoadingTasks::class).sample(Duration.ofMillis(500)).map { it.loading }

    override suspend fun findAllTasks(): List<TaskEntity> = taskService.findAllAsync()

    override fun onNewTask(): Flow<TaskEntity> =
        EventBus.events.filterIsInstance(TaskCreateEvent::class).map { it.taskEntity }

    override fun onUpdateTask(): Flow<TaskEntity> =
        EventBus.events.filterIsInstance(TaskUpdateEvent::class).map { it.taskEntity }

    override suspend fun clearTasks() = taskService.clearAsync()

    override fun onClearTasks(): Flow<Unit> =
        EventBus.events.filterIsInstance(TaskClearEvent::class).map { }

    override suspend fun getSettings(): Settings = settingsService.settings

    override suspend fun saveSettings(settings: Settings) = settingsService.newSettings(settings)

    override suspend fun getProxies(): List<String> = settingsService.getProxies()

    override fun onUpdateSettings(): Flow<Settings> =
        EventBus.events.filterIsInstance(SettingsUpdateEvent::class).map { it.settings }

    override suspend fun loggedInUser(): String = vgAuthService.loggedUser

    override suspend fun getVersion(): String = ApplicationProperties.VERSION

    override suspend fun getServerInfo(): ServerInfo {
        val appDir = VRIPPER_DIR
        val database = appDir.resolve("vripper.db")
        val logFile = appDir.resolve("vripper.log")
        val settingsFile = appDir.resolve("config.json")
        val runtime = Runtime.getRuntime()
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        val settings = settingsService.settings
        return ServerInfo(
            version = ApplicationProperties.VERSION.trim(),
            applicationFolder = appDir.pathString,
            databasePath = database.pathString,
            databaseExists = Files.isRegularFile(database),
            databaseSize = fileSize(database),
            logPath = logFile.pathString,
            logExists = Files.isRegularFile(logFile),
            logSize = fileSize(logFile),
            settingsPath = settingsFile.pathString,
            settingsExists = Files.isRegularFile(settingsFile),
            settingsSize = fileSize(settingsFile),
            downloadPath = settings.downloadSettings.downloadPath,
            tempPath = settings.systemSettings.tempPath,
            operatingSystem = "${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
                    "(${System.getProperty("os.arch")})",
            javaVersion = System.getProperty("java.version"),
            javaVendor = System.getProperty("java.vendor"),
            uptimeMillis = ManagementFactory.getRuntimeMXBean().uptime,
            threadCount = ManagementFactory.getThreadMXBean().threadCount,
            heapUsed = runtime.totalMemory() - runtime.freeMemory(),
            heapCommitted = runtime.totalMemory(),
            heapMax = runtime.maxMemory(),
            processors = runtime.availableProcessors(),
            gcCount = gcBeans.sumOf { it.collectionCount.coerceAtLeast(0) },
            gcTimeMillis = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) },
        )
    }

    private fun fileSize(path: NioPath): Long =
        runCatching { if (Files.isRegularFile(path)) Files.size(path) else 0L }.getOrDefault(0L)

    override suspend fun move(postEntityId: Long, position: MovePosition) {
        downloadService.move(postEntityId, position)
    }

    override fun downloadImage(downloadRequest: DownloadRequest): Flow<ImageChunk> = flow {
        val imageEntity = dataAccessService.findImageByIdAsync(downloadRequest.imageId)
            .orElseThrow { NoSuchElementException("Image with id = ${downloadRequest.imageId} does not exist") }
        val postEntity = dataAccessService.findPostByEntityIdAsync(imageEntity.postEntityId)
        val filePath = Path(postEntity.downloadDirectory)
            .resolve(postEntity.folderName)
            .resolve(imageEntity.filename)

        if (imageEntity.filename.isBlank() || !filePath.exists()) {
            emit(
                ImageChunk(
                    missing = true,
                    imageId = downloadRequest.imageId,
                    offset = 0,
                    data = ByteArray(0),
                    isLast = true
                )
            )
            return@flow
        }

        val chunkSize = 256 * 1024
        val totalSize = Files.size(filePath)
        if (totalSize == 0L) {
            emit(
                ImageChunk(
                    missing = false,
                    imageId = downloadRequest.imageId,
                    offset = 0,
                    data = ByteArray(0),
                    isLast = true
                )
            )
            return@flow
        }

        Files.newInputStream(filePath).use { input ->
            val buffer = ByteArray(chunkSize)
            var offset = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                offset += read
                val isLast = offset >= totalSize
                emit(
                    ImageChunk(
                        missing = false,
                        imageId = downloadRequest.imageId,
                        offset = offset - read,
                        data = buffer.copyOf(read),
                        isLast = isLast
                    )
                )
                if (isLast) {
                    break
                }
            }
        }
    }

    override suspend fun dbMigration(): String = withContext(Dispatchers.IO) {

        val conn = try {
            DriverManager.getConnection("jdbc:h2:file:$VRIPPER_DIR/vripper;DB_CLOSE_DELAY=-1;IFEXISTS=TRUE")
        } catch (_: JdbcSQLNonTransientConnectionException) {
            return@withContext "Old database not found, nothing to do"
        }

        var postsCount = 0
        var threadCount = 0

        conn.use { conn ->
            conn.prepareStatement("select * from post").use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val done = resultSet.getInt("DONE")
                        val hosts = resultSet.getString("HOSTS")
                        val outputPath = resultSet.getString("OUTPUT_PATH")
                        val postId = resultSet.getLong("POST_ID")
                        val status = resultSet.getString("STATUS")
                        val threadId = resultSet.getLong("THREAD_ID")
                        val postTitle = resultSet.getString("POST_TITLE")
                        val threadTitle = resultSet.getString("THREAD_TITLE")
                        val forum = resultSet.getString("FORUM")
                        val total = resultSet.getInt("TOTAL")
                        val size = resultSet.getLong("SIZE")
                        val downloaded = resultSet.getLong("DOWNLOADED")
                        val url = resultSet.getString("URL")
                        val token = resultSet.getString("TOKEN")
                        val addedAt = resultSet.getTimestamp("ADDED_AT")
                        val folderName = resultSet.getString("FOLDER_NAME") ?: ""

                        val exists = dataAccessService.existsPostIdAsync(postId)
                        if (exists) {
                            continue
                        }

                        val post = PostEntity(
                            postTitle = postTitle,
                            threadTitle = threadTitle,
                            forum = forum,
                            url = url,
                            token = token,
                            vgPostId = postId,
                            vgThreadId = threadId,
                            total = total,
                            hosts = hosts.split(";").dropLastWhile { it.isEmpty() }.toSet(),
                            downloadDirectory = outputPath,
                            addedOn = addedAt.toLocalDateTime(),
                            folderName = folderName,
                            status = Status.valueOf(status),
                            done = done,
                            size = size,
                            downloaded = downloaded,
                        )

                        val images = mutableListOf<ImageEntity>()

                        // load images
                        conn.prepareStatement("select * from image where post_id = ?").use { statement ->
                            statement.setLong(1, postId)
                            statement.executeQuery().use { set ->
                                while (set.next()) {
                                    val downloaded = set.getLong("DOWNLOADED")
                                    val host = set.getByte("HOST")
                                    val index = set.getInt("INDEX")
                                    val status = Status.valueOf(set.getString("STATUS"))
                                    val size = set.getLong("SIZE")
                                    val url = set.getString("URL")
                                    val thumbUrl = set.getString("THUMB_URL")
                                    val fileName = set.getString("FILENAME") ?: ""

                                    ImageEntity(
                                        url = url,
                                        thumbUrl = thumbUrl,
                                        host = host,
                                        index = index,
                                        size = size,
                                        downloaded = downloaded,
                                        status = status,
                                        filename = fileName,
                                    ).also { images.add(it) }
                                }
                            }
                        }

                        val savedPost = dataAccessService.saveAndNotifyAsync(post, images)

                        //load meta
                        conn.prepareStatement("select * from metadata where post_id = ?").use { statement ->
                            statement.setLong(1, postId)
                            statement.executeQuery().use { set ->
                                if (set.next()) {
                                    val data = Json.decodeFromString(set.getString("DATA")) as MetadataEntity.Data
                                    val metadata = MetadataEntity(
                                        postIdRef = savedPost.id, data = data
                                    )
                                    dataAccessService.saveMetadataAsync(metadata)
                                }
                            }
                        }
                        postsCount++
                    }
                }
            }

            conn.prepareStatement("select * from thread").use { statement ->
                statement.executeQuery().use { set ->
                    while (set.next()) {
                        val total = set.getInt("TOTAL")
                        val url = set.getString("URL")
                        val threadId = set.getLong("THREAD_ID")
                        val title = set.getString("TITLE")

                        if (dataAccessService.findThreadByThreadIdAsync(threadId).isPresent) {
                            continue
                        }

                        val threadEntity = ThreadEntity(
                            title = title,
                            link = url,
                            threadId = threadId,
                            total = total,
                        )

                        dataAccessService.saveThreadAsync(threadEntity)
                        threadCount++
                    }
                }
            }
        }

        if (postsCount == 0 && threadCount == 0) {
            "Nothing to do, everything was imported"
        } else {
            "Successfully imported $postsCount posts and $threadCount threads"
        }
    }

    override fun connectionState(): String {
        return ""
    }
}