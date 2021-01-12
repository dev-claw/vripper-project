package tn.mnlr.vripper.services;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.mnlr.vripper.jpa.domain.Image;
import tn.mnlr.vripper.jpa.domain.Metadata;
import tn.mnlr.vripper.jpa.domain.Post;
import tn.mnlr.vripper.jpa.domain.Queued;
import tn.mnlr.vripper.jpa.domain.enums.Status;
import tn.mnlr.vripper.jpa.repositories.IImageRepository;
import tn.mnlr.vripper.jpa.repositories.IMetadataRepository;
import tn.mnlr.vripper.jpa.repositories.IPostRepository;
import tn.mnlr.vripper.jpa.repositories.IQueuedRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class DataService {

    private final IPostRepository postRepository;
    private final IImageRepository imageRepository;
    private final IQueuedRepository queuedRepository;
    private final IMetadataRepository metadataRepository;
    private final SettingsService settingsService;

    @Autowired
    public DataService(IPostRepository postRepository, IImageRepository imageRepository, IQueuedRepository queuedRepository, IMetadataRepository metadataRepository, SettingsService settingsService) {
        this.postRepository = postRepository;
        this.imageRepository = imageRepository;
        this.queuedRepository = queuedRepository;
        this.metadataRepository = metadataRepository;
        this.settingsService = settingsService;
    }

    private void save(Post post) {
        postRepository.save(post);
    }

    private void save(Queued queued) {
        queuedRepository.save(queued);
    }

    private void save(Image image) {
        imageRepository.save(image);
    }

    public boolean exists(String postId) {
        return postRepository.existByPostId(postId);
    }

    public void newPost(Post post, Collection<Image> images) {
        save(post);
        images.forEach(image -> {
            image.setPostIdRef(post.getId());
            save(image);
        });
    }

    public void setDownloadingToStopped() {
        postRepository.setDownloadingToStopped();
    }

    public synchronized void afterJobFinish(Image image, Post post) {
        if (image.getStatus().equals(Status.COMPLETE)) {
            post.setDone(post.getDone() + 1);
            updatePostDone(post.getDone(), post.getId());
        } else if (image.getStatus().equals(Status.ERROR)) {
            post.setStatus(Status.PARTIAL);
            updatePostStatus(post.getStatus(), post.getId());
        }
    }

    private void updatePostDone(int done, Long id) {
        postRepository.updateDone(done, id);
    }

    public void finishPost(@NonNull Post post) {
        if (!imageRepository.findByPostIdAndIsError(post.getPostId()).isEmpty()) {
            post.setStatus(Status.ERROR);
            updatePostStatus(post.getStatus(), post.getId());
        } else {
            if (post.getDone() < post.getTotal()) {
                post.setStatus(Status.STOPPED);
                updatePostStatus(post.getStatus(), post.getId());
            } else {
                post.setStatus(Status.COMPLETE);
                updatePostStatus(post.getStatus(), post.getId());
                if (settingsService.getSettings().getClearCompleted()) {
                    remove(post.getPostId());
                }
            }
        }
    }

    private void remove(@NonNull final String postId) {
        imageRepository.deleteAllByPostId(postId);
        metadataRepository.deleteByPostId(postId);
        postRepository.deleteByPostId(postId);
    }

    public void newQueueLink(@NonNull final Queued queued) {
        save(queued);
    }

    public void removeQueueLink(@NonNull final String threadId) {
        queuedRepository.deleteByThreadId(threadId);
    }

    public List<String> clearCompleted() {
        List<String> completed = postRepository.findCompleted();
        completed.forEach(this::remove);
        return completed;
    }

    public void removeAll(final List<String> postIds) {
        if (postIds != null && !postIds.isEmpty()) {
            for (String postId : postIds) {
                remove(postId);
            }
        } else {
            postRepository.findAll().forEach(p -> remove(p.getPostId()));
        }
    }

    public List<Image> findByPostIdAndIsNotCompleted(@NonNull String postId) {
        return imageRepository.findByPostIdAndIsNotCompleted(postId);

    }

    public long countErrorImages() {
        return imageRepository.countError();
    }

    public List<Image> findImagesByPostId(String postId) {
        return imageRepository.findByPostId(postId);
    }

    public Iterable<Post> findAllPosts() {
        return postRepository.findAll();
    }

    public Optional<Post> findPostByPostId(String postId) {
        return postRepository.findByPostId(postId);
    }

    public void stopImagesByPostIdAndIsNotCompleted(String postId) {
        imageRepository.stopByPostIdAndIsNotCompleted(postId);
    }

    public Optional<Queued> findQueuedByThreadId(String threadId) {
        return queuedRepository.findByThreadId(threadId);
    }

    public Iterable<Queued> findAllQueued() {
        return queuedRepository.findAll();
    }

    public Optional<Post> findById(Long aLong) {
        return postRepository.findById(aLong);
    }

    public Optional<Image> findImageById(Long aLong) {
        return imageRepository.findById(aLong);
    }

    public Optional<Queued> findQueuedById(Long aLong) {
        return queuedRepository.findById(aLong);
    }

    public void setMetadata(Post post, Metadata metadata) {
        metadata.setPostIdRef(post.getId());
        metadataRepository.save(metadata);
    }

    public Optional<Metadata> findMetadataByPostId(String postId) {
        return metadataRepository.findByPostId(postId);
    }

    public void updateImageStatus(Status status, Long id) {
        imageRepository.updateStatus(status, id);
    }

    public void updateImageCurrent(long current, Long id) {
        imageRepository.updateCurrent(current, id);
    }

    public void updateImageTotal(long total, Long id) {
        imageRepository.updateTotal(total, id);
    }

    public void updatePostStatus(Status status, Long id) {
        postRepository.updateStatus(status, id);
    }

    public void updateDownloadDirectory(String postFolderName, Long id) {
        postRepository.updateFolderName(postFolderName, id);
    }

    public void updatePostTitle(String title, Long id) {
        postRepository.updateTitle(title, id);
    }

    public void updatePostThanked(boolean thanked, Long id) {
        postRepository.updateThanked(thanked, id);
    }
}
