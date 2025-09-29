package me.vripper.download;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import me.vripper.host.Host;
import me.vripper.jpa.domain.Image;
import me.vripper.jpa.domain.Post;
import me.vripper.jpa.domain.enums.Status;
import me.vripper.services.DataService;
import me.vripper.services.MetadataService;
import me.vripper.services.SettingsService;
import me.vripper.services.domain.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DownloadService {

  private final int MAX_POOL_SIZE = 12;

  private final SettingsService settingsService;
  private final DataService dataService;
  private final MetadataService metadataService;
  private final List<Host> hosts;

  private final Map<Host, AtomicInteger> threadCount = new HashMap<>();
  private final ExecutorService executor = Executors.newFixedThreadPool(MAX_POOL_SIZE);
  private final List<DownloadJob> running = new ArrayList<>();
  private final List<DownloadJob> pending = new ArrayList<>();

  private final Thread pollThread;

  @Autowired
  public DownloadService(
      SettingsService settingsService,
      DataService dataService,
      MetadataService metadataService,
      List<Host> hosts) {
    this.settingsService = settingsService;
    this.dataService = dataService;
    this.metadataService = metadataService;
    this.hosts = hosts;
    pollThread =
        new Thread(
            () -> {
              while (!Thread.interrupted()) {
                try {
                  synchronized (this) {
                    List<DownloadJob> accepted = new ArrayList<>();
                    List<DownloadJob> candidates = getCandidates(candidateCount());
                    candidates.forEach(
                        c -> {
                          if (canRun(c.getImage().getHost())) {
                            accepted.add(c);
                          }
                        });
                    pending.removeAll(accepted);
                    accepted.forEach(this::schedule);
                    accepted.clear();
                    this.wait();
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                }
              }
            },
            "Download scheduler thread");
  }

  @PostConstruct
  private void init() {
    pollThread.start();
  }

  public void destroy() throws Exception {
    log.info("Shutting down ExecutionService");
    pollThread.interrupt();
    executor.shutdown();
    dataService
        .findAllPosts()
        .forEach(
            p -> {
              log.debug(String.format("Stopping download jobs for %s", p));
              this.stopRunning(p.getPostId());
            });
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
      log.warn("Some jobs are still running!, forcing shutdown");
      executor.shutdownNow();
    }
  }

  private synchronized void stopRunning(@NonNull String postId) {
    List<DownloadJob> stopping = new ArrayList<>();
    Iterator<DownloadJob> iterator = running.iterator();
    while (iterator.hasNext()) {
      DownloadJob downloadJob = iterator.next();
      if (postId.equals(downloadJob.getPost().getPostId())) {
        downloadJob.stop();
        iterator.remove();
        stopping.add(downloadJob);
      }
    }

    while (!stopping.isEmpty()) {
      stopping.removeIf(DownloadJob::isFinished);
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public void stopAll(List<String> postIds) {
    stop(
        Objects.requireNonNullElseGet(
            postIds,
            () ->
                dataService.findAllPosts().stream()
                    .map(Post::getPostId)
                    .collect(Collectors.toList())));
  }

  public void restartAll(List<String> posIds) {
    restart(
        Objects.requireNonNullElseGet(
            posIds,
            () ->
                dataService.findAllPosts().stream()
                    .map(Post::getPostId)
                    .collect(Collectors.toList())));
  }

  private void restart(@NonNull List<String> postIds) {
    Map<Post, Collection<Image>> data = new HashMap<>();
    for (String postId : postIds) {
      if (isPending(postId)) {
        log.warn(
            String.format("Cannot restart, jobs are currently running for post id %s", postIds));
        continue;
      }
      List<Image> images = dataService.findByPostIdAndIsNotCompleted(postId);
      if (images.isEmpty()) {
        continue;
      }
      Post post = dataService.findPostByPostId(postId).orElseThrow();
      log.debug(String.format("Restarting %d jobs for post id %s", images.size(), postIds));
      data.put(post, images);
    }
    enqueue(data);
  }

  private synchronized boolean isPending(String postId) {
    return pending.stream().anyMatch(p -> p.getPost().getPostId().equals(postId));
  }

  private synchronized boolean isRunning(String postId) {
    return running.stream().anyMatch(p -> p.getPost().getPostId().equals(postId));
  }

  private synchronized void stop(List<String> postIds) {

    for (String postId : postIds) {
      final Post post = dataService.findPostByPostId(postId).orElseThrow();
      if (post == null) {
        continue;
      }
      pending.removeIf(p -> p.getPost().equals(post));
      stopRunning(postId);
      dataService.stopImagesByPostIdAndIsNotCompleted(postId);
      dataService.finishPost(post);
    }
    metadataService.stopFetchingMetadata(postIds);
  }

  private boolean canRun(Host host) {
    boolean canRun;
    int totalRunning = threadCount.values().stream().mapToInt(AtomicInteger::get).sum();
    canRun =
        threadCount.get(host).get() < settingsService.getSettings().getMaxThreads()
            && (settingsService.getSettings().getMaxTotalThreads() == 0
                ? totalRunning < MAX_POOL_SIZE
                : totalRunning < settingsService.getSettings().getMaxTotalThreads());
    if (canRun) {
      threadCount.get(host).incrementAndGet();
      return true;
    }
    return false;
  }

  private Map<Host, Integer> candidateCount() {
    HashMap<Host, Integer> map = new HashMap<>();
    hosts.forEach(
        h -> {
          AtomicInteger count = threadCount.get(h);
          if (count == null) {
            count = new AtomicInteger(0);
            threadCount.put(h, count);
          }
          map.put(h, settingsService.getSettings().getMaxThreads() - count.get());
        });
    return map;
  }

  private List<DownloadJob> getCandidates(Map<Host, Integer> candidateCount) {
    List<DownloadJob> candidates = new ArrayList<>();
    for (DownloadJob downloadJob : pending) {
      Host host = downloadJob.getImage().getHost();
      Integer maxPerHost = candidateCount.get(host);
      if (maxPerHost > 0) {
        candidates.add(downloadJob);
        candidateCount.put(host, maxPerHost - 1);
      }
    }
    return candidates;
  }

  public void enqueue(Map<Post, Collection<Image>> images) {
    synchronized (this) {
      for (Map.Entry<Post, Collection<Image>> entry : images.entrySet()) {
        entry.getKey().setStatus(Status.PENDING);
        dataService.updatePostStatus(entry.getKey().getStatus(), entry.getKey().getId());
        for (Image image : entry.getValue()) {
          log.debug(String.format("Enqueuing a job for %s", image.getUrl()));
          image.init();
          dataService.updateImageStatus(image.getStatus(), image.getId());
          dataService.updateImageCurrent(image.getCurrent(), image.getId());
          DownloadJob downloadJob =
              new DownloadJob(
                  entry.getKey(), image, (Settings) settingsService.getSettings().clone());
          pending.add(downloadJob);
        }
      }
      pending.sort(Comparator.comparing(e -> e.getPost().getAddedOn()));
      this.notify();
    }
  }

  private synchronized void schedule(DownloadJob downloadJob) {
    log.debug(String.format("Scheduling a job for %s", downloadJob.getImage().getUrl()));
    executor.execute(new DownloadJobWrapper(downloadJob));
    running.add(downloadJob);
  }

  public void afterJobFinish(DownloadJob downloadJob) {
    synchronized (this) {
      running.remove(downloadJob);
      threadCount.get(downloadJob.getImage().getHost()).decrementAndGet();
      if (!isPending(downloadJob.getPost().getPostId())
          && !isRunning(downloadJob.getPost().getPostId())) {
        dataService.finishPost(downloadJob.getPost());
      }
      this.notify();
    }
  }

  public int pendingCount() {
    return pending.size();
  }

  public int runningCount() {
    return running.size();
  }
}
