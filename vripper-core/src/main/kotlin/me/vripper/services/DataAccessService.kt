package me.vripper.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.vripper.data.repositories.ImageRepository
import me.vripper.data.repositories.MetadataRepository
import me.vripper.data.repositories.PostRepository
import me.vripper.data.repositories.ThreadRepository
import me.vripper.entities.*
import me.vripper.event.*
import me.vripper.model.ErrorCount
import me.vripper.utilities.LoggerDelegate
import me.vripper.utilities.PathUtils
import me.vripper.utilities.PathUtils.sanitize
import me.vripper.vgapi.PostItem
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

internal data class PostsSnapshot(
    val posts: List<PostEntity>,
    val images: List<ImageEntity>,
    val metadata: List<MetadataEntity>,
)

internal data class PostSnapshot(
    val post: PostEntity,
    val images: List<ImageEntity>,
    val metadata: MetadataEntity?,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class DataAccessService(
    private val settingsService: SettingsService,
    private val postRepository: PostRepository,
    private val imageRepository: ImageRepository,
    private val threadRepository: ThreadRepository,
    private val metadataRepository: MetadataRepository,
    private val eventBus: EventBus,
) {

    private val log by LoggerDelegate()
    private val newPostsMutex = Mutex()

    private val postEntityIdCache: LoadingCache<Long, PostEntity> =
        Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build { id ->
            transaction { postRepository.findById(id) }
        }

    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    fun saveAndNotify(postEntity: PostEntity, images: List<ImageEntity>): PostEntity {
        val savedPost = transaction {
            val savedPost = postRepository.save(listOf(postEntity)).first()
            imageRepository.save(images.map { it.copy(postEntityId = savedPost.id) })
            savedPost
        }
        eventBus.publishEvent(PostCreateEvent(listOf(savedPost)))
        return savedPost
    }

    suspend fun saveAndNotifyAsync(postEntity: PostEntity, images: List<ImageEntity>): PostEntity {
        val savedPost = dbQuery {
            val savedPost = postRepository.save(listOf(postEntity)).first()
            imageRepository.save(images.map { it.copy(postEntityId = savedPost.id) })
            savedPost
        }
        eventBus.publishEvent(PostCreateEvent(listOf(savedPost)))
        return savedPost
    }

    fun updatePosts(postEntities: List<PostEntity>) {
        transaction { postRepository.update(postEntities) }
        postEntities.forEach { postEntity ->
            postEntityIdCache.put(postEntity.id, postEntity)
        }
        log.debug("[{}] Publishing event: PostUpdateEvent for {} posts", System.currentTimeMillis(), postEntities.size)
        eventBus.publishEvent(PostUpdateEvent(postEntities))
    }

    suspend fun updatePostsAsync(postEntities: List<PostEntity>) {
        dbQuery { postRepository.update(postEntities) }
        postEntities.forEach { postEntity ->
            postEntityIdCache.put(postEntity.id, postEntity)
        }
        log.debug("[{}] Publishing event: PostUpdateEvent for {} posts", System.currentTimeMillis(), postEntities.size)
        eventBus.publishEvent(PostUpdateEvent(postEntities))
    }

    fun updatePost(postEntity: PostEntity) {
        transaction { postRepository.update(postEntity) }
        postEntityIdCache.put(postEntity.id, postEntity)
        log.debug("[{}] Publishing event: PostUpdateEvent for post {}", System.currentTimeMillis(), postEntity.id)
        eventBus.publishEvent(PostUpdateEvent(listOf(postEntity)))
    }

    suspend fun updatePostAsync(postEntity: PostEntity) {
        dbQuery { postRepository.update(postEntity) }
        postEntityIdCache.put(postEntity.id, postEntity)
        log.debug("[{}] Publishing event: PostUpdateEvent for post {}", System.currentTimeMillis(), postEntity.id)
        eventBus.publishEvent(PostUpdateEvent(listOf(postEntity)))
    }

    fun save(threadEntity: ThreadEntity) {
        val savedThread = transaction { threadRepository.save(threadEntity) }
        log.debug("[{}] Publishing event: ThreadCreateEvent for thread {}", System.currentTimeMillis(), savedThread.id)
        eventBus.publishEvent(ThreadCreateEvent(savedThread))
    }

    suspend fun saveThreadAsync(threadEntity: ThreadEntity) {
        val savedThread = dbQuery { threadRepository.save(threadEntity) }
        log.debug("[{}] Publishing event: ThreadCreateEvent for thread {}", System.currentTimeMillis(), savedThread.id)
        eventBus.publishEvent(ThreadCreateEvent(savedThread))
    }

    fun update(threadEntity: ThreadEntity) {
        transaction { threadRepository.update(threadEntity) }
        log.debug("[{}] Publishing event: ThreadUpdateEvent for thread {}", System.currentTimeMillis(), threadEntity.id)
        eventBus.publishEvent(ThreadUpdateEvent(threadEntity))
    }

    fun updateImages(imageEntities: List<ImageEntity>) {
        transaction { imageRepository.update(imageEntities) }
        log.debug("[{}] Publishing event: ImageEvent for {} images", System.currentTimeMillis(), imageEntities.size)
        eventBus.publishEvent(ImageEvent(imageEntities))
    }

    suspend fun updateImagesAsync(imageEntities: List<ImageEntity>) {
        dbQuery { imageRepository.update(imageEntities) }
        log.debug("[{}] Publishing event: ImageEvent for {} images", System.currentTimeMillis(), imageEntities.size)
        eventBus.publishEvent(ImageEvent(imageEntities))
    }

    fun updateImage(imageEntity: ImageEntity, persist: Boolean = true) {
        if (persist) {
            transaction {
                imageRepository.update(imageEntity)
            }
        }
        log.debug("[{}] Publishing event: ImageEvent for image {}", System.currentTimeMillis(), imageEntity.id)
        eventBus.publishEvent(ImageEvent(listOf(imageEntity)))
    }

    suspend fun updateImageAsync(imageEntity: ImageEntity, persist: Boolean = true) {
        if (persist) {
            dbQuery { imageRepository.update(imageEntity) }
        }
        log.debug("[{}] Publishing event: ImageEvent for image {}", System.currentTimeMillis(), imageEntity.id)
        eventBus.publishEvent(ImageEvent(listOf(imageEntity)))
    }

    fun exists(postEntityId: Long): Boolean {
        if (postEntityIdCache.getIfPresent(postEntityId) != null) {
            return true
        }
        return transaction { postRepository.existByPostEntityId(postEntityId) }
    }

    suspend fun existsAsync(postEntityId: Long): Boolean {
        if (postEntityIdCache.getIfPresent(postEntityId) != null) {
            return true
        }
        return dbQuery { postRepository.existByPostEntityId(postEntityId) }
    }

    fun existsPostId(postId: Long): Boolean {
        return transaction { postRepository.existByPostId(postId) }
    }

    suspend fun existsPostIdAsync(postId: Long): Boolean {
        return dbQuery { postRepository.existByPostId(postId) }
    }

    @Synchronized
    fun newPosts(postItems: List<PostItem>): List<PostEntity> {
        val posts = buildPosts(postItems)
        val imagesByVgPostId = posts.entries.associate { (postEntity, images) ->
            postEntity.vgPostId to images
        }
        val savedPosts = transaction {
            val savedPosts = postRepository.save(posts.keys.toList())
            savedPosts.forEach { postEntity ->
                imagesByVgPostId[postEntity.vgPostId]?.let { images ->
                    imageRepository.save(images.map { it.copy(postEntityId = postEntity.id) })
                }
            }
            savedPosts
        }
        eventBus.publishEvent(PostCreateEvent(savedPosts))
        return savedPosts
    }

    suspend fun newPostsAsync(postItems: List<PostItem>): List<PostEntity> {
        val savedPosts = newPostsMutex.withLock {
            val posts = buildPosts(postItems)
            val imagesByVgPostId = posts.entries.associate { (postEntity, images) ->
                postEntity.vgPostId to images
            }
            dbQuery {
                val savedPosts = postRepository.save(posts.keys.toList())
                savedPosts.forEach { postEntity ->
                    imagesByVgPostId[postEntity.vgPostId]?.let { images ->
                        imageRepository.save(images.map { it.copy(postEntityId = postEntity.id) })
                    }
                }
                savedPosts
            }
        }
        eventBus.publishEvent(PostCreateEvent(savedPosts))
        return savedPosts
    }

    private fun buildPosts(postItems: List<PostItem>): Map<PostEntity, List<ImageEntity>> {
        val settings = settingsService.settings
        return postItems.associate { postItem ->
            val postEntity = PostEntity(
                postTitle = postItem.title,
                url = postItem.url,
                token = postItem.securityToken,
                vgPostId = postItem.postId,
                vgThreadId = postItem.threadId,
                total = postItem.imageCount,
                hosts = postItem.hosts.map { "${it.first} (${it.second})" }.toSet(),
                threadTitle = postItem.threadTitle,
                forum = postItem.forum,
                downloadDirectory = PathUtils.calculateDownloadPath(
                    postItem.forum,
                    postItem.threadTitle,
                    settings
                ).pathString,
                folderName = if (settings.downloadSettings.appendPostId) "${sanitize(postItem.title)}_${postItem.postId}" else sanitize(
                    postItem.title
                )
            )
            val imageEntities = postItem.imageItemList.mapIndexed { index, imageItem ->
                ImageEntity(
                    url = imageItem.mainLink,
                    thumbUrl = imageItem.thumbLink,
                    host = imageItem.host.hostId,
                    index = index,
                )
            }
            Pair(postEntity, imageEntities)
        }
    }

    fun finishPost(id: Long, automatic: Boolean = false) {
        val post = findPostByEntityId(id)
        when {
            countErrorsByPostEntityId(id) > 0 -> {
                post.status = Status.ERROR
                updatePost(post)
            }

            post.done < post.total -> {
                post.status = Status.STOPPED
                updatePost(post)
            }

            else -> {
                post.status = Status.FINISHED
                updatePost(post)
                if (settingsService.settings.downloadSettings.clearCompleted && automatic) {
                    remove(listOf(post.id))
                }
            }
        }
    }

    private fun countErrorsByPostEntityId(postEntityId: Long): Int {
        return transaction { imageRepository.countByPostEntityIdAndIsError(postEntityId) }
    }

    private fun remove(postEntityIds: List<Long>) {
        transaction {
            metadataRepository.deleteAllByPostEntityId(postEntityIds)
            imageRepository.deleteAllByPostEntityId(postEntityIds)
            postRepository.deleteAll(postEntityIds)
        }
        postEntityIdCache.invalidateAll(postEntityIds)
        log.debug("[{}] Publishing event: PostDeleteEvent for {} posts", System.currentTimeMillis(), postEntityIds.size)
        eventBus.publishEvent(PostDeleteEvent(postEntityIds = postEntityIds))
        log.debug("[{}] Publishing event: ErrorCountEvent after post deletion", System.currentTimeMillis())
        eventBus.publishEvent(ErrorCountEvent(ErrorCount(countImagesInError())))
    }

    private suspend fun removeAsync(postEntityIds: List<Long>) {
        dbQuery {
            metadataRepository.deleteAllByPostEntityId(postEntityIds)
            imageRepository.deleteAllByPostEntityId(postEntityIds)
            postRepository.deleteAll(postEntityIds)
        }
        postEntityIdCache.invalidateAll(postEntityIds)
        log.debug("[{}] Publishing event: PostDeleteEvent for {} posts", System.currentTimeMillis(), postEntityIds.size)
        eventBus.publishEvent(PostDeleteEvent(postEntityIds = postEntityIds))
        log.debug("[{}] Publishing event: ErrorCountEvent after post deletion", System.currentTimeMillis())
        eventBus.publishEvent(ErrorCountEvent(ErrorCount(countImagesInErrorAsync())))
    }

    fun removeThread(threadId: Long) {
        transaction { threadRepository.deleteByThreadId(threadId) }
        log.debug("[{}] Publishing event: ThreadDeleteEvent for thread {}", System.currentTimeMillis(), threadId)
        eventBus.publishEvent(ThreadDeleteEvent(threadId))
    }

    suspend fun removeThreadAsync(threadId: Long) {
        dbQuery { threadRepository.deleteByThreadId(threadId) }
        log.debug("[{}] Publishing event: ThreadDeleteEvent for thread {}", System.currentTimeMillis(), threadId)
        eventBus.publishEvent(ThreadDeleteEvent(threadId))
    }

    fun clearCompleted(): List<Long> {
        val completed = transaction { postRepository.findCompleted() }
        remove(completed)
        return completed
    }

    suspend fun clearCompletedAsync(): List<Long> {
        val completed = dbQuery { postRepository.findCompleted() }
        removeAsync(completed)
        return completed
    }

    fun removeAll(postEntityIds: List<Long> = emptyList()) {
        remove(if (postEntityIds.isNotEmpty()) postEntityIds else findAllPostIds())
    }

    suspend fun removeAllAsync(postEntityIds: List<Long> = emptyList()) {
        removeAsync(if (postEntityIds.isNotEmpty()) postEntityIds else findAllPostIdsAsync())
    }

    fun stopImagesByPostEntityIdAndIsNotCompleted(postEntityId: Long) {
        transaction { imageRepository.stopByPostEntityIdAndIsNotCompleted(postEntityId) }
    }

    suspend fun stopImagesByPostEntityIdAndIsNotCompletedAsync(postEntityId: Long) {
        dbQuery { imageRepository.stopByPostEntityIdAndIsNotCompleted(postEntityId) }
    }

    fun stopImagesByPostEntityIdAndIsNotCompleted() {
        transaction { imageRepository.stopByPostEntityIdAndIsNotCompleted() }
    }

    suspend fun stopImagesByPostEntityIdAndIsNotCompletedAsync() {
        dbQuery { imageRepository.stopByPostEntityIdAndIsNotCompleted() }
    }

    fun saveMetadata(metadataEntity: MetadataEntity) {
        transaction { metadataRepository.save(metadataEntity) }
        log.debug(
            "[{}] Publishing event: MetadataUpdateEvent for post {}",
            System.currentTimeMillis(),
            metadataEntity.postIdRef
        )
        eventBus.publishEvent(MetadataUpdateEvent(metadataEntity))
    }

    suspend fun saveMetadataAsync(metadataEntity: MetadataEntity) {
        dbQuery { metadataRepository.save(metadataEntity) }
        log.debug(
            "[{}] Publishing event: MetadataUpdateEvent for post {}",
            System.currentTimeMillis(),
            metadataEntity.postIdRef
        )
        eventBus.publishEvent(MetadataUpdateEvent(metadataEntity))
    }

    fun clearQueueLinks() {
        transaction { threadRepository.deleteAll() }
        log.debug("[{}] Publishing event: ThreadClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(ThreadClearEvent())
    }

    suspend fun clearQueueLinksAsync() {
        dbQuery { threadRepository.deleteAll() }
        log.debug("[{}] Publishing event: ThreadClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(ThreadClearEvent())
    }

    fun setDownloadingToStopped() {
        transaction { postRepository.setDownloadingToStopped() }
    }

    suspend fun setDownloadingToStoppedAsync() {
        dbQuery { postRepository.setDownloadingToStopped() }
    }

    fun findAllPosts(): List<PostEntity> {
        return transaction { postRepository.findAll() }
    }

    suspend fun findAllPostsSnapshotAsync(): PostsSnapshot {
        return dbQuery {
            PostsSnapshot(
                posts = postRepository.findAll(),
                images = imageRepository.findAll(),
                metadata = metadataRepository.findAll(),
            )
        }
    }

    suspend fun findPostSnapshotAsync(id: Long): PostSnapshot {
        val post = findPostByEntityIdAsync(id)
        return dbQuery {
            PostSnapshot(
                post = post,
                images = imageRepository.findByPostEntityId(id),
                metadata = metadataRepository.findByPostEntityId(id).orElse(null),
            )
        }
    }

    fun findPostByEntityId(id: Long): PostEntity {
        return postEntityIdCache.get(id) ?: throw NoSuchElementException("Post with id = $id does not exist")
    }

    suspend fun findPostByEntityIdAsync(id: Long): PostEntity {
        postEntityIdCache.getIfPresent(id)?.let { return it }
        val post = dbQuery { postRepository.findById(id) }
            ?: throw NoSuchElementException("Post with id = $id does not exist")
        postEntityIdCache.put(id, post)
        return post
    }

    fun findAllPostIds(): List<Long> {
        return transaction { postRepository.findAllIds() }
    }

    suspend fun findAllPostIdsAsync(): List<Long> {
        return dbQuery { postRepository.findAllIds() }
    }

    fun findImagesByPostEntityId(postEntityId: Long): List<ImageEntity> {
        return transaction { imageRepository.findByPostEntityId(postEntityId) }
    }

    suspend fun findImagesByPostEntityIdAsync(postEntityId: Long): List<ImageEntity> {
        return dbQuery { imageRepository.findByPostEntityId(postEntityId) }
    }

    fun findImageById(id: Long): Optional<ImageEntity> {
        return transaction { imageRepository.findById(id) }
    }

    suspend fun findImageByIdAsync(id: Long): Optional<ImageEntity> {
        return dbQuery { imageRepository.findById(id) }
    }

    fun findAllThreads(): List<ThreadEntity> {
        return transaction { threadRepository.findAll() }
    }

    suspend fun findAllThreadsAsync(): List<ThreadEntity> {
        return dbQuery { threadRepository.findAll() }
    }

    fun findThreadById(id: Long): Optional<ThreadEntity> {
        return transaction {
            threadRepository.findById(id)
        }
    }

    fun findByPostEntityIdAndIsNotCompleted(postEntityId: Long): List<ImageEntity> {
        return transaction { imageRepository.findByPostEntityIdAndIsNotCompleted(postEntityId) }
    }

    fun countImagesInError(): Int {
        return transaction { imageRepository.countError() }
    }

    suspend fun countImagesInErrorAsync(): Int {
        return dbQuery { imageRepository.countError() }
    }

    fun findThreadByThreadId(threadId: Long): Optional<ThreadEntity> {
        return transaction { threadRepository.findByThreadId(threadId) }
    }

    suspend fun findThreadByThreadIdAsync(threadId: Long): Optional<ThreadEntity> {
        return dbQuery { threadRepository.findByThreadId(threadId) }
    }

    fun findAllNonCompletedPostEntityIds(): List<Long> {
        return transaction { postRepository.findAllNonCompletedPostEntityIds() }
    }

    fun findMetadataByPostEntityId(postEntityId: Long): Optional<MetadataEntity> {
        return transaction { metadataRepository.findByPostEntityId(postEntityId) }
    }

    suspend fun findMetadataByPostEntityIdAsync(postEntityId: Long): Optional<MetadataEntity> {
        return dbQuery { metadataRepository.findByPostEntityId(postEntityId) }
    }
}
