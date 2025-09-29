package me.vripper.services;

import java.util.*;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import me.vripper.jpa.domain.*;
import me.vripper.jpa.domain.enums.Status;
import me.vripper.jpa.repositories.IImageRepository;
import me.vripper.jpa.repositories.IMetadataRepository;
import me.vripper.jpa.repositories.IPostRepository;
import me.vripper.jpa.repositories.IQueuedRepository;
import me.vripper.jpa.repositories.impl.LogEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
public class DataService {

  private final IPostRepository postRepository;
  private final IImageRepository imageRepository;
  private final IQueuedRepository queuedRepository;
  private final IMetadataRepository metadataRepository;
  private final SettingsService settingsService;
  private final LogEventRepository eventRepository;

  @Autowired
  public DataService(
      IPostRepository postRepository,
      IImageRepository imageRepository,
      IQueuedRepository queuedRepository,
      IMetadataRepository metadataRepository,
      SettingsService settingsService,
      LogEventRepository eventRepository) {
    this.postRepository = postRepository;
    this.imageRepository = imageRepository;
    this.queuedRepository = queuedRepository;
    this.metadataRepository = metadataRepository;
    this.settingsService = settingsService;
    this.eventRepository = eventRepository;
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
    images.forEach(
        image -> {
          image.setPostIdRef(post.getId());
          save(image);
        });
    sortPostsByRank();
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

  public void updatePostStatus(Status status, Long id) {
    postRepository.updateStatus(status, id);
  }

  public void updatePostThanks(boolean thanked, Long id) {
    postRepository.updateThanked(thanked, id);
  }

  public void updatePostTitle(String title, Long id) {
    postRepository.updateTitle(title, id);
  }

  public void updatePostDownloadDirectory(String postFolderName, Long id) {
    postRepository.updateFolderName(postFolderName, id);
  }

  public void updatePostRank(int rank, Long id) {
    postRepository.updateRank(rank, id);
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
          remove(List.of(post.getPostId()));
        }
      }
    }
  }

  private void remove(@NonNull final List<String> postIds) {
    for (String postId : postIds) {
      imageRepository.deleteAllByPostId(postId);
      metadataRepository.deleteByPostId(postId);
      postRepository.deleteByPostId(postId);
    }
    sortPostsByRank();
  }

  public void newQueueLink(@NonNull final Queued queued) {
    save(queued);
  }

  public void removeQueueLink(@NonNull final String threadId) {
    queuedRepository.deleteByThreadId(threadId);
  }

  public List<String> clearCompleted() {
    List<String> completed = postRepository.findCompleted();
    remove(completed);
    return completed;
  }

  public void removeAll(final List<String> postIds) {

    remove(
        Objects.requireNonNullElse(
            postIds,
            postRepository.findAll().stream().map(Post::getPostId).collect(Collectors.toList())));
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

  public List<Post> findAllPosts() {
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

  public List<Queued> findAllQueued() {
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

  public synchronized void setMetadata(Post post, Metadata metadata) {
    if (metadataRepository.findByPostId(post.getPostId()).isEmpty()) {
      metadata.setPostIdRef(post.getId());
      metadataRepository.save(metadata);
    }
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

  public Optional<LogEvent> findEventById(Long id) {
    return eventRepository.findById(id);
  }

  public List<LogEvent> findAllEvents() {
    return eventRepository.findAll();
  }

  public void clearQueueLinks() {
    queuedRepository.deleteAll();
  }

  public synchronized void sortPostsByRank() {
    List<Post> posts = findAllPosts();
    posts.sort(Comparator.comparing(Post::getAddedOn));
    for (int i = 0; i < posts.size(); i++) {
      posts.get(i).setRank(i);
      updatePostRank(i, posts.get(i).getId());
    }
  }
}
