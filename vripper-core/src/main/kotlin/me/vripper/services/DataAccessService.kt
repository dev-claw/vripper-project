package me.vripper.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.vripper.data.repositories.ImageRepository
import me.vripper.data.repositories.MetadataRepository
import me.vripper.data.repositories.PostActionRepository
import me.vripper.data.repositories.PostRepository
import me.vripper.data.repositories.SubscriptionRepository
import me.vripper.data.repositories.ThreadRepository
import me.vripper.entities.*
import me.vripper.event.*
import me.vripper.model.ErrorCount
import me.vripper.utilities.DatabaseManager
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
    private val subscriptionRepository: SubscriptionRepository,
    private val metadataRepository: MetadataRepository,
    private val postActionRepository: PostActionRepository,
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

    private fun <T> writeTransaction(block: Transaction.() -> T): T =
        DatabaseManager.writeTransaction(block)

    private suspend fun <T> writeTransactionAsync(block: Transaction.() -> T): T =
        DatabaseManager.writeTransactionAsync(block)

    fun saveAndNotify(postEntity: PostEntity, images: List<ImageEntity>): PostEntity {
        val savedPost = writeTransaction {
            val savedPost = postRepository.save(listOf(postEntity)).first()
            imageRepository.save(images.map { it.copy(postEntityId = savedPost.id) })
            savedPost
        }
        eventBus.publishEvent(PostCreateEvent(listOf(savedPost)))
        return savedPost
    }

    suspend fun saveAndNotifyAsync(postEntity: PostEntity, images: List<ImageEntity>): PostEntity {
        val savedPost = writeTransactionAsync {
            val savedPost = postRepository.save(listOf(postEntity)).first()
            imageRepository.save(images.map { it.copy(postEntityId = savedPost.id) })
            savedPost
        }
        eventBus.publishEvent(PostCreateEvent(listOf(savedPost)))
        return savedPost
    }

    fun updatePosts(postEntities: List<PostEntity>) {
        writeTransaction { postRepository.update(postEntities) }
        postEntities.forEach { postEntity ->
            postEntityIdCache.put(postEntity.id, postEntity)
        }
        log.debug("[{}] Publishing event: PostUpdateEvent for {} posts", System.currentTimeMillis(), postEntities.size)
        eventBus.publishEvent(PostUpdateEvent(postEntities))
    }

    suspend fun updatePostsAsync(postEntities: List<PostEntity>) {
        writeTransactionAsync { postRepository.update(postEntities) }
        postEntities.forEach { postEntity ->
            postEntityIdCache.put(postEntity.id, postEntity)
        }
        log.debug("[{}] Publishing event: PostUpdateEvent for {} posts", System.currentTimeMillis(), postEntities.size)
        eventBus.publishEvent(PostUpdateEvent(postEntities))
    }

    fun updatePost(postEntity: PostEntity) {
        writeTransaction { postRepository.update(postEntity) }
        postEntityIdCache.put(postEntity.id, postEntity)
        log.debug("[{}] Publishing event: PostUpdateEvent for post {}", System.currentTimeMillis(), postEntity.id)
        eventBus.publishEvent(PostUpdateEvent(listOf(postEntity)))
    }

    suspend fun updatePostAsync(postEntity: PostEntity) {
        writeTransactionAsync { postRepository.update(postEntity) }
        postEntityIdCache.put(postEntity.id, postEntity)
        log.debug("[{}] Publishing event: PostUpdateEvent for post {}", System.currentTimeMillis(), postEntity.id)
        eventBus.publishEvent(PostUpdateEvent(listOf(postEntity)))
    }

    fun save(threadEntity: ThreadEntity) {
        val savedThread = writeTransaction { threadRepository.save(threadEntity) }
        log.debug("[{}] Publishing event: ThreadCreateEvent for thread {}", System.currentTimeMillis(), savedThread.id)
        eventBus.publishEvent(ThreadCreateEvent(savedThread))
    }

    suspend fun saveThreadAsync(threadEntity: ThreadEntity) {
        val savedThread = writeTransactionAsync { threadRepository.save(threadEntity) }
        log.debug("[{}] Publishing event: ThreadCreateEvent for thread {}", System.currentTimeMillis(), savedThread.id)
        eventBus.publishEvent(ThreadCreateEvent(savedThread))
    }

    fun update(threadEntity: ThreadEntity) {
        writeTransaction { threadRepository.update(threadEntity) }
        log.debug("[{}] Publishing event: ThreadUpdateEvent for thread {}", System.currentTimeMillis(), threadEntity.id)
        eventBus.publishEvent(ThreadUpdateEvent(threadEntity))
    }

    fun saveSubscription(subscriptionEntity: SubscriptionEntity) {
        val saved = writeTransaction { subscriptionRepository.save(subscriptionEntity) }
        log.debug("[{}] Publishing event: SubscriptionCreateEvent for thread {}", System.currentTimeMillis(), saved.threadId)
        eventBus.publishEvent(SubscriptionCreateEvent(saved))
    }

    suspend fun saveSubscriptionAsync(subscriptionEntity: SubscriptionEntity) {
        val saved = writeTransactionAsync { subscriptionRepository.save(subscriptionEntity) }
        log.debug("[{}] Publishing event: SubscriptionCreateEvent for thread {}", System.currentTimeMillis(), saved.threadId)
        eventBus.publishEvent(SubscriptionCreateEvent(saved))
    }

    fun updateSubscription(subscriptionEntity: SubscriptionEntity) {
        writeTransaction { subscriptionRepository.update(subscriptionEntity) }
        log.debug("[{}] Publishing event: SubscriptionUpdateEvent for thread {}", System.currentTimeMillis(), subscriptionEntity.threadId)
        eventBus.publishEvent(SubscriptionUpdateEvent(subscriptionEntity))
    }

    suspend fun updateSubscriptionAsync(subscriptionEntity: SubscriptionEntity) {
        writeTransactionAsync { subscriptionRepository.update(subscriptionEntity) }
        log.debug("[{}] Publishing event: SubscriptionUpdateEvent for thread {}", System.currentTimeMillis(), subscriptionEntity.threadId)
        eventBus.publishEvent(SubscriptionUpdateEvent(subscriptionEntity))
    }

    suspend fun removeSubscriptionAsync(threadId: Long) {
        writeTransactionAsync { subscriptionRepository.deleteByThreadId(threadId) }
        log.debug("[{}] Publishing event: SubscriptionDeleteEvent for thread {}", System.currentTimeMillis(), threadId)
        eventBus.publishEvent(SubscriptionDeleteEvent(threadId))
    }

    suspend fun clearSubscriptionsAsync() {
        writeTransactionAsync { subscriptionRepository.deleteAll() }
        log.debug("[{}] Publishing event: SubscriptionClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(SubscriptionClearEvent())
    }

    suspend fun findAllSubscriptionsAsync(): List<SubscriptionEntity> {
        return dbQuery { subscriptionRepository.findAll() }
    }

    suspend fun findSubscriptionByThreadIdAsync(threadId: Long): Optional<SubscriptionEntity> {
        return dbQuery { subscriptionRepository.findByThreadId(threadId) }
    }

    fun updateImages(imageEntities: List<ImageEntity>) {
        writeTransaction { imageRepository.update(imageEntities) }
        log.debug("[{}] Publishing event: ImageEvent for {} images", System.currentTimeMillis(), imageEntities.size)
        eventBus.publishEvent(ImageEvent(imageEntities))
    }

    suspend fun updateImagesAsync(imageEntities: List<ImageEntity>) {
        writeTransactionAsync { imageRepository.update(imageEntities) }
        log.debug("[{}] Publishing event: ImageEvent for {} images", System.currentTimeMillis(), imageEntities.size)
        eventBus.publishEvent(ImageEvent(imageEntities))
    }

    fun updateImage(imageEntity: ImageEntity, persist: Boolean = true) {
        if (persist) {
            writeTransaction {
                imageRepository.update(imageEntity)
            }
        }
        log.debug("[{}] Publishing event: ImageEvent for image {}", System.currentTimeMillis(), imageEntity.id)
        eventBus.publishEvent(ImageEvent(listOf(imageEntity)))
    }

    suspend fun updateImageAsync(imageEntity: ImageEntity, persist: Boolean = true) {
        if (persist) {
            writeTransactionAsync { imageRepository.update(imageEntity) }
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
        val savedPosts = writeTransaction {
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
            writeTransactionAsync {
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
                author = postItem.author,
                userId = postItem.userId,
                postDate = postItem.date,
                edited = postItem.edited,
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
                if (settingsService.settings.automationSettings.isActive()) {
                    eventBus.publishEvent(PostCompletedEvent(post.id))
                } else if (settingsService.settings.downloadSettings.clearCompleted && automatic) {
                    remove(listOf(post.id))
                }
            }
        }
    }

    fun countErrorsByPostEntityId(postEntityId: Long): Int {
        return transaction { imageRepository.countByPostEntityIdAndIsError(postEntityId) }
    }

    private fun remove(postEntityIds: List<Long>) {
        writeTransaction {
            postActionRepository.deleteAllByPostIds(postEntityIds)
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
        writeTransactionAsync {
            postActionRepository.deleteAllByPostIds(postEntityIds)
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
        writeTransaction { threadRepository.deleteByThreadId(threadId) }
        log.debug("[{}] Publishing event: ThreadDeleteEvent for thread {}", System.currentTimeMillis(), threadId)
        eventBus.publishEvent(ThreadDeleteEvent(threadId))
    }

    suspend fun removeThreadAsync(threadId: Long) {
        writeTransactionAsync { threadRepository.deleteByThreadId(threadId) }
        log.debug("[{}] Publishing event: ThreadDeleteEvent for thread {}", System.currentTimeMillis(), threadId)
        eventBus.publishEvent(ThreadDeleteEvent(threadId))
    }

    fun clearCompleted(): List<Long> {
        val completed = transaction {
            postRepository.findCompleted().filter { isActionTerminal(it) }
        }
        remove(completed)
        return completed
    }

    suspend fun clearCompletedAsync(): List<Long> {
        val completed = dbQuery {
            postRepository.findCompleted().filter { isActionTerminal(it) }
        }
        removeAsync(completed)
        return completed
    }

    private fun isActionTerminal(postEntityId: Long): Boolean {
        val action = postActionRepository.findByPostId(postEntityId) ?: return true
        return action.state == ActionState.DONE ||
            action.state == ActionState.FAILED ||
            action.state == ActionState.SKIPPED ||
            action.state == ActionState.NONE
    }

    fun removeAll(postEntityIds: List<Long> = emptyList()) {
        remove(if (postEntityIds.isNotEmpty()) postEntityIds else findAllPostIds())
    }

    suspend fun removeAllAsync(postEntityIds: List<Long> = emptyList()) {
        removeAsync(if (postEntityIds.isNotEmpty()) postEntityIds else findAllPostIdsAsync())
    }

    fun stopImagesByPostEntityIdAndIsNotCompleted(postEntityId: Long) {
        writeTransaction { imageRepository.stopByPostEntityIdAndIsNotCompleted(postEntityId) }
    }

    suspend fun stopImagesByPostEntityIdAndIsNotCompletedAsync(postEntityId: Long) {
        writeTransactionAsync { imageRepository.stopByPostEntityIdAndIsNotCompleted(postEntityId) }
    }

    fun stopImagesByPostEntityIdAndIsNotCompleted() {
        writeTransaction { imageRepository.stopByPostEntityIdAndIsNotCompleted() }
    }

    suspend fun stopImagesByPostEntityIdAndIsNotCompletedAsync() {
        writeTransactionAsync { imageRepository.stopByPostEntityIdAndIsNotCompleted() }
    }

    fun saveMetadata(metadataEntity: MetadataEntity) {
        writeTransaction { metadataRepository.save(metadataEntity) }
        log.debug(
            "[{}] Publishing event: MetadataUpdateEvent for post {}",
            System.currentTimeMillis(),
            metadataEntity.postIdRef
        )
        eventBus.publishEvent(MetadataUpdateEvent(metadataEntity))
    }

    suspend fun saveMetadataAsync(metadataEntity: MetadataEntity) {
        writeTransactionAsync { metadataRepository.save(metadataEntity) }
        log.debug(
            "[{}] Publishing event: MetadataUpdateEvent for post {}",
            System.currentTimeMillis(),
            metadataEntity.postIdRef
        )
        eventBus.publishEvent(MetadataUpdateEvent(metadataEntity))
    }

    fun clearQueueLinks() {
        writeTransaction { threadRepository.deleteAll() }
        log.debug("[{}] Publishing event: ThreadClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(ThreadClearEvent())
    }

    suspend fun clearQueueLinksAsync() {
        writeTransactionAsync { threadRepository.deleteAll() }
        log.debug("[{}] Publishing event: ThreadClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(ThreadClearEvent())
    }

    fun setDownloadingToStopped() {
        writeTransaction { postRepository.setDownloadingToStopped() }
    }

    suspend fun setDownloadingToStoppedAsync() {
        writeTransactionAsync { postRepository.setDownloadingToStopped() }
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

    fun updateCustomFields(postEntityId: Long, customFields: List<CustomField>): MetadataEntity {
        val metadata = writeTransaction {
            val existing = metadataRepository.findByPostEntityId(postEntityId).orElse(null)
            val data = existing?.data?.copy(customFields = customFields)
                ?: MetadataEntity.Data("", emptyList(), customFields)
            metadataRepository.save(MetadataEntity(postEntityId, data))
        }
        eventBus.publishEvent(MetadataUpdateEvent(metadata))
        return metadata
    }

    fun findPostAction(postEntityId: Long): PostActionEntity? {
        return transaction { postActionRepository.findByPostId(postEntityId) }
    }

    suspend fun findPostActionAsync(postEntityId: Long): PostActionEntity? {
        return dbQuery { postActionRepository.findByPostId(postEntityId) }
    }

    fun findAllPostActions(): List<PostActionEntity> {
        return transaction { postActionRepository.findAll() }
    }

    fun findNonTerminalPostActions(): List<PostActionEntity> {
        return transaction { postActionRepository.findNonTerminal() }
    }

    fun savePostAction(postAction: PostActionEntity): PostActionEntity {
        return writeTransaction { postActionRepository.save(postAction) }
    }

    fun updatePostAction(postAction: PostActionEntity): PostActionEntity {
        val updated = postAction.copy(updatedAt = java.time.LocalDateTime.now())
        writeTransaction { postActionRepository.update(updated) }
        return updated
    }

    fun deletePostAction(postEntityId: Long) {
        writeTransaction { postActionRepository.deleteByPostId(postEntityId) }
    }
}
