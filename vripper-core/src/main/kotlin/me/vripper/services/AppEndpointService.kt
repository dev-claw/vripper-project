package me.vripper.services

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.time.sample
import kotlinx.serialization.json.Json
import me.vripper.entities.*
import me.vripper.event.*
import me.vripper.exception.PostParseException
import me.vripper.model.*
import me.vripper.tasks.AddPostTask
import me.vripper.tasks.ThreadLookupTask
import me.vripper.utilities.ApplicationProperties
import me.vripper.utilities.ApplicationProperties.VRIPPER_DIR
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils
import me.vripper.utilities.taskRunner
import org.h2.jdbc.JdbcSQLNonTransientConnectionException
import java.sql.DriverManager
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import kotlin.concurrent.withLock
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.jvm.optionals.getOrNull

internal class AppEndpointService(
    private val downloadService: DownloadService,
    private val dataTransaction: DataTransaction,
    private val threadCacheService: ThreadCacheService,
    private val settingsService: SettingsService,
    private val vgAuthService: VGAuthService,
    private val logService: LogService,
) : IAppEndpointService {

    private val log by LoggerDelegate()
    private val lock = ReentrantLock()

    override suspend fun scanLinks(postLinks: String) {
        lock.withLock {
            if (postLinks.isBlank()) {
                return
            }
            val urlList = postLinks.split(Pattern.compile("\\r?\\n")).dropLastWhile { it.isBlank() }.map { it.trim() }
                .filter { it.isNotEmpty() }
            for (link in urlList) {
                log.debug("Scanning: $link")
                if (!link.startsWith(settingsService.settings.viperSettings.host)) {
                    continue
                }
                var threadId: Long
                var postId: Long?
                val m = Pattern.compile(
                    Pattern.quote(settingsService.settings.viperSettings.host) + "/threads/(\\d+)((.*p=)(\\d+))?"
                ).matcher(link)
                if (m.find()) {
                    threadId = m.group(1).toLong()
                    postId = m.group(4)?.toLong()
                    if (postId == null) {
                        taskRunner.submit(
                            ThreadLookupTask(
                                threadId, settingsService.settings
                            )
                        )
                    } else {
                        taskRunner.submit(
                            AddPostTask(
                                listOf(ThreadPostId(threadId, postId))
                            )
                        )
                    }
                } else {
                    log.error("Invalid link $link, link is missing the threadId")
                    continue
                }
            }
        }
    }

    override suspend fun restartAll(posIds: List<Long>) {
        lock.withLock {
            downloadService.restartAll(posIds.filter { dataTransaction.exists(it) }
                .map { dataTransaction.findPostByPostId(it) })
        }
    }

    override suspend fun download(posts: List<ThreadPostId>) {
        taskRunner.submit(AddPostTask(posts))
    }

    override suspend fun stopAll(postIdList: List<Long>) {
        lock.withLock {
            downloadService.stop(postIdList)
        }
    }

    override suspend fun remove(postIdList: List<Long>) {
        lock.withLock {
            downloadService.stop(postIdList)
            dataTransaction.removeAll(postIdList)
        }
    }

    override suspend fun clearCompleted(): List<Long> {
        lock.withLock {
            return dataTransaction.clearCompleted()
        }
    }

    override suspend fun grab(threadId: Long): List<PostSelection> {
        lock.withLock {
            return try {
                val thread = dataTransaction.findThreadByThreadId(threadId).orElseThrow {
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
                dataTransaction.removeThread(it)
            }
        }
    }

    override suspend fun threadClear() {
        lock.withLock {
            dataTransaction.clearQueueLinks()
        }
    }

    override suspend fun renameToFirst(postIds: List<Long>) {
        postIds.forEach { postId ->
            dataTransaction
                .findMetadataByPostId(postId)
                .map { it.data.resolvedNames }
                .filter { it.isNotEmpty() }
                .getOrNull()?.let { rename(postId, it.first()) }
        }
    }

    override suspend fun rename(postId: Long, newName: String) {
        taskRunner.submit {
            synchronized(postId.toString().intern()) {
                if (dataTransaction.exists(postId)) {
                    dataTransaction.findPostByPostId(postId).let { post ->
                        if (Path(post.downloadDirectory, post.folderName).exists()) {
                            PathUtils.rename(
                                dataTransaction.findImagesByPostId(postId),
                                post.downloadDirectory,
                                post.folderName,
                                newName
                            )
                        }
                        post.folderName = PathUtils.sanitize(newName)
                        dataTransaction.updatePost(post)
                    }
                }
            }
        }
    }

    override fun onNewPosts(): Flow<Post> =
        EventBus.events.filterIsInstance(PostCreateEvent::class).map { it.postEntities.map(::mapper) }
            .flatMapConcat { it.asFlow() }


    override fun onUpdatePosts() =
        EventBus.events.filterIsInstance(PostUpdateEvent::class).map { it.postEntities.map(::mapper) }
            .flatMapConcat { it.asFlow() }


    override fun onDeletePosts() =
        EventBus.events.filterIsInstance(PostDeleteEvent::class).flatMapConcat { it.postIds.asFlow() }


    override fun onUpdateMetadata() =
        EventBus.events.filterIsInstance(MetadataUpdateEvent::class).map { it.metadataEntity }


    override suspend fun findAllPosts(): List<Post> {
        return dataTransaction.findAllPosts().map(::mapper)
    }

    private fun mapper(postEntity: PostEntity): Post {
        val metadata: Metadata? = dataTransaction.findMetadataByPostId(postEntity.postId).orElse(null)
        val images = dataTransaction.findImagesByPostId(postEntity.postId)
        return Post(
            postEntity.id,
            postEntity.postTitle,
            postEntity.threadTitle,
            postEntity.forum,
            postEntity.url,
            postEntity.token,
            postEntity.postId,
            postEntity.threadId,
            postEntity.total,
            postEntity.hosts,
            postEntity.downloadDirectory,
            postEntity.addedOn,
            postEntity.folderName,
            postEntity.status,
            postEntity.done,
            postEntity.rank,
            postEntity.size,
            postEntity.downloaded,
            images.take(4).map { it.thumbUrl },
            metadata?.data?.postedBy ?: "",
            metadata?.data?.resolvedNames ?: emptyList(),
        )
    }

    override suspend fun findPost(postId: Long): Post {
        return mapper(dataTransaction.findPostByPostId(postId))
    }

    override suspend fun findImagesByPostId(postId: Long): List<Image> {
        return dataTransaction.findImagesByPostId(postId)
    }

    override fun onUpdateImagesByPostId(postId: Long): Flow<Image> =
        EventBus.events.filterIsInstance(ImageEvent::class).map {
            it.imageEntities.filter { imageEntity: Image -> imageEntity.postId == postId }
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
        return dataTransaction.findAllThreads()
    }

    override fun onDownloadSpeed(): Flow<DownloadSpeed> =
        EventBus.events.filterIsInstance(DownloadSpeedEvent::class).map { it.downloadSpeed }

    override fun onVGUserUpdate(): Flow<String> =
        EventBus.events.filterIsInstance(VGUserLoginEvent::class).map { it.username }

    override fun onQueueStateUpdate(): Flow<QueueState> =
        EventBus.events.filterIsInstance(QueueStateEvent::class).map { it.queueState }

    override fun onErrorCountUpdate(): Flow<ErrorCount> =
        EventBus.events.filterIsInstance(ErrorCountEvent::class).map { it.errorCount }

    override fun onTasksRunning(): Flow<Boolean> =
        EventBus.events.filterIsInstance(LoadingTasks::class).sample(Duration.ofMillis(500)).map { it.loading }

    override suspend fun getSettings(): Settings = settingsService.settings

    override suspend fun saveSettings(settings: Settings) = settingsService.newSettings(settings)

    override suspend fun getProxies(): List<String> = settingsService.getProxies()

    override fun onUpdateSettings(): Flow<Settings> =
        EventBus.events.filterIsInstance(SettingsUpdateEvent::class).map { it.settings }

    override suspend fun loggedInUser(): String = vgAuthService.loggedUser

    override suspend fun getVersion(): String = ApplicationProperties.VERSION

    override suspend fun dbMigration(): String {

        val conn = try {
            DriverManager
                .getConnection("jdbc:h2:file:$VRIPPER_DIR/vripper;DB_CLOSE_DELAY=-1;IFEXISTS=TRUE")
        } catch (_: JdbcSQLNonTransientConnectionException) {
            return "Old database not found, nothing to do"
        }

        var postsCount = 0;
        var threadCount = 0;

        conn.use { conn ->
            conn.prepareStatement("select * from post").use {
                it.executeQuery().use {
                    while (it.next()) {
                        val done = it.getInt("DONE")
                        val hosts = it.getString("HOSTS")
                        val outputPath = it.getString("OUTPUT_PATH")
                        val postId = it.getLong("POST_ID")
                        val status = it.getString("STATUS")
                        val threadId = it.getLong("THREAD_ID")
                        val postTitle = it.getString("POST_TITLE")
                        val threadTitle = it.getString("THREAD_TITLE")
                        val forum = it.getString("FORUM")
                        val total = it.getInt("TOTAL")
                        val size = it.getLong("SIZE")
                        val downloaded = it.getLong("DOWNLOADED")
                        val url = it.getString("URL")
                        val token = it.getString("TOKEN")
                        val addedAt = it.getTimestamp("ADDED_AT")
                        val folderName = it.getString("FOLDER_NAME") ?: ""

                        val exists = dataTransaction.exists(postId)
                        if (exists) {
                            continue
                        }

                        val post = PostEntity(
                            postTitle = postTitle,
                            threadTitle = threadTitle,
                            forum = forum,
                            url = url,
                            token = token,
                            postId = postId,
                            threadId = threadId,
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
                                        postId = postId,
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

                        dataTransaction.saveAndNotify(post, images)

                        //load meta
                        conn.prepareStatement("select * from metadata where post_id = ?").use { statement ->
                            statement.setLong(1, postId)
                            statement.executeQuery().use { set ->
                                if (set.next()) {
                                    val data = Json.decodeFromString(set.getString("DATA")) as MetadataEntity.Data
                                    val metadata = MetadataEntity(
                                        postId = postId,
                                        data = data
                                    )
                                    dataTransaction.saveMetadata(metadata)
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

                        if (dataTransaction.findThreadByThreadId(threadId).isPresent) {
                            continue
                        }

                        val threadEntity = ThreadEntity(
                            title = title,
                            link = url,
                            threadId = threadId,
                            total = total,
                        )

                        dataTransaction.save(threadEntity)
                        threadCount++
                    }
                }
            }
        }

        return if (postsCount == 0 && threadCount == 0) {
            "Nothing to do, everything was imported"
        } else {
            "Successfully imported $postsCount posts and $threadCount threads"
        }
    }
}