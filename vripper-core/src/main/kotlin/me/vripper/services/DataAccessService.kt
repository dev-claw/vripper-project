package me.vripper.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
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
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

internal class DataAccessService(
    private val settingsService: SettingsService,
    private val postRepository: PostRepository,
    private val imageRepository: ImageRepository,
    private val threadRepository: ThreadRepository,
    private val metadataRepository: MetadataRepository,
    private val eventBus: EventBus,
) {

    private val log by LoggerDelegate()

    private val postEntityIdCache: LoadingCache<Long, PostEntity> =
        Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build { id ->
            transaction { postRepository.findById(id) }
        }

    private fun save(postEntities: List<PostEntity>): List<PostEntity> {
        return transaction { postRepository.save(postEntities) }
    }

    fun saveAndNotify(postEntity: PostEntity, images: List<ImageEntity>): PostEntity {
        val savedPost = transaction {
            val savedPost =
                postRepository.save(listOf(postEntity)).first()
            save(images.map { it.copy(postEntityId = savedPost.id) })
            // Publish event inside transaction for consistency
            eventBus.publishEvent(PostCreateEvent(listOf(savedPost)))
            savedPost
        }
        return savedPost
    }

    fun updatePosts(postEntities: List<PostEntity>) {
        transaction { postRepository.update(postEntities) }
        postEntities.forEach { postEntity ->
            postEntityIdCache.put(postEntity.id, postEntity)
        }
        // Log and publish event after transaction commit but before returning
        log.debug("[{}] Publishing event: PostUpdateEvent for {} posts", System.currentTimeMillis(), postEntities.size)
        eventBus.publishEvent(PostUpdateEvent(postEntities))
    }

    fun updatePost(postEntity: PostEntity) {
        transaction { postRepository.update(postEntity) }
        postEntityIdCache.put(postEntity.id, postEntity)
        // Log and publish event after transaction commit but before returning
        log.debug("[{}] Publishing event: PostUpdateEvent for post {}", System.currentTimeMillis(), postEntity.id)
        eventBus.publishEvent(PostUpdateEvent(listOf(postEntity)))
    }

    fun save(threadEntity: ThreadEntity) {
        val savedThread = transaction { threadRepository.save(threadEntity) }
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

    fun updateImage(imageEntity: ImageEntity, persist: Boolean = true) {
        if (persist) {
            transaction {
                imageRepository.update(imageEntity)
            }
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

    fun existsPostId(postId: Long): Boolean {
        return transaction { postRepository.existByPostId(postId) }
    }

    @Synchronized
    fun newPosts(postItems: List<PostItem>): List<PostEntity> {
        val posts = postItems.associate { postItem ->
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
                    settingsService.settings
                ).pathString,
                folderName = if (settingsService.settings.downloadSettings.appendPostId) "${sanitize(postItem.title)}_${postItem.postId}" else sanitize(
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


        val savedPosts = transaction {
            val savedPosts = save(posts.keys.toList())
            savedPosts.associateWith { postEntity ->
                posts.entries.find { postItem -> postItem.key.vgPostId == postEntity.vgPostId }?.value ?: emptyList()
            }.forEach { (key, value) ->
                save(value.map { it.copy(postEntityId = key.id) })
            }
            savedPosts
        }
        eventBus.publishEvent(PostCreateEvent(savedPosts))
        return savedPosts
    }

    private fun save(imageEntities: List<ImageEntity>) {
        transaction { imageRepository.save(imageEntities) }
    }

    fun finishPost(id: Long, automatic: Boolean = false) {
        val post = findPostByEntityId(id)
        val imagesInErrorStatus = findByIdAndIsError(id)
        if (imagesInErrorStatus.isNotEmpty()) {
            post.status = Status.ERROR
            updatePost(post)
        } else {
            if (post.done < post.total) {
                post.status = Status.STOPPED
                updatePost(post)
            } else {
                post.status = Status.FINISHED
                transaction {
                    updatePost(post)
                    if (settingsService.settings.downloadSettings.clearCompleted && automatic) {
                        remove(listOf(post.id))
                    }
                }
            }
        }
    }

    private fun findByIdAndIsError(postEntityId: Long): List<ImageEntity> {
        return transaction { imageRepository.findByPostEntityIdAndIsError(postEntityId) }
    }

    private fun remove(postEntityIds: List<Long>) {

        transaction {
            metadataRepository.deleteAllByPostEntityId(postEntityIds)
            imageRepository.deleteAllByPostEntityId(postEntityIds)
            postRepository.deleteAll(postEntityIds)
        }
        postEntityIds.forEach { postEntityId ->
            postEntityIdCache.get(postEntityId)?.let { postEntityIdCache.invalidate(it.id) }
            postEntityIdCache.invalidate(postEntityId)
        }
        log.debug("[{}] Publishing event: PostDeleteEvent for {} posts", System.currentTimeMillis(), postEntityIds.size)
        eventBus.publishEvent(PostDeleteEvent(postEntityIds = postEntityIds))
        log.debug("[{}] Publishing event: ErrorCountEvent after post deletion", System.currentTimeMillis())
        eventBus.publishEvent(ErrorCountEvent(ErrorCount(countImagesInError())))
    }

    fun removeThread(threadId: Long) {
        transaction { threadRepository.deleteByThreadId(threadId) }
        log.debug("[{}] Publishing event: ThreadDeleteEvent for thread {}", System.currentTimeMillis(), threadId)
        eventBus.publishEvent(ThreadDeleteEvent(threadId))
    }

    fun clearCompleted(): List<Long> {
        val completed = transaction { postRepository.findCompleted() }
        remove(completed)
        return completed
    }

    fun removeAll(postEntityIds: List<Long> = emptyList()) {
        if (postEntityIds.isNotEmpty()) {
            remove(postEntityIds)
        } else {
            remove(findAllPosts().map(PostEntity::id))
        }
    }

    fun stopImagesByPostEntityIdAndIsNotCompleted(postEntityId: Long) {
        transaction { imageRepository.stopByPostEntityIdAndIsNotCompleted(postEntityId) }
    }

    fun stopImagesByPostEntityIdAndIsNotCompleted() {
        transaction { imageRepository.stopByPostEntityIdAndIsNotCompleted() }
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

    fun clearQueueLinks() {
        transaction { threadRepository.deleteAll() }
        log.debug("[{}] Publishing event: ThreadClearEvent", System.currentTimeMillis())
        eventBus.publishEvent(ThreadClearEvent())
    }

    fun setDownloadingToStopped() {
        transaction { postRepository.setDownloadingToStopped() }
    }

    fun findAllPosts(): List<PostEntity> {
        return transaction { postRepository.findAll() }
    }

    fun findPostByEntityId(id: Long): PostEntity {
        return postEntityIdCache.get(id) ?: throw NoSuchElementException("Post with id = $id does not exist")
    }

    fun findImagesByPostEntityId(postEntityId: Long): List<ImageEntity> {
        return transaction { imageRepository.findByPostEntityId(postEntityId) }
    }

    fun findImageById(id: Long): Optional<ImageEntity> {
        return transaction { imageRepository.findById(id) }
    }

    fun findAllThreads(): List<ThreadEntity> {
        return transaction { threadRepository.findAll() }
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

    fun findThreadByThreadId(threadId: Long): Optional<ThreadEntity> {
        return transaction { threadRepository.findByThreadId(threadId) }
    }

    fun findAllNonCompletedPostEntityIds(): List<Long> {
        return transaction { postRepository.findAllNonCompletedPostEntityIds() }
    }

    fun findMetadataByPostEntityId(postEntityId: Long): Optional<MetadataEntity> {
        return transaction { metadataRepository.findByPostEntityId(postEntityId) }
    }
}