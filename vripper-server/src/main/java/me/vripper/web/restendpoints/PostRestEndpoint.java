package me.vripper.web.restendpoints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import me.vripper.download.DownloadService;
import me.vripper.jpa.domain.Metadata;
import me.vripper.jpa.domain.Post;
import me.vripper.jpa.domain.Queued;
import me.vripper.services.*;
import me.vripper.services.domain.MultiPostItem;
import me.vripper.services.domain.MultiPostScanResult;
import me.vripper.tasks.AddPostRunnable;
import me.vripper.tasks.LinkScanRunnable;
import me.vripper.web.restendpoints.domain.*;
import me.vripper.web.restendpoints.exceptions.BadRequestException;
import me.vripper.web.restendpoints.exceptions.NotFoundException;
import me.vripper.web.restendpoints.exceptions.ServerErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin(value = "*")
public class PostRestEndpoint {

  private static final Object LOCK = new Object();
  private final DataService dataService;
  private final PathService pathService;
  private final DownloadService downloadService;
  private final PostService postService;
  private final ThreadPoolService threadPoolService;
  private final SettingsService settingsService;

  @Autowired
  public PostRestEndpoint(
      DataService dataService,
      PathService pathService,
      DownloadService downloadService,
      PostService postService,
      ThreadPoolService threadPoolService,
      SettingsService settingsService) {
    this.dataService = dataService;
    this.pathService = pathService;
    this.downloadService = downloadService;
    this.postService = postService;
    this.threadPoolService = threadPoolService;
    this.settingsService = settingsService;
  }

  @PostMapping("/post")
  @ResponseStatus(code = HttpStatus.OK)
  public void processPost(@RequestBody ThreadUrl _url) {
    synchronized (LOCK) {
      if (_url.getUrl() == null || _url.getUrl().isBlank()) {
        log.error("Cannot process empty requests");
        throw new BadRequestException("Cannot process empty requests");
      }
      List<String> urlList =
          Arrays.stream(_url.getUrl().split("\\r?\\n"))
              .map(String::trim)
              .filter(e -> !e.isEmpty())
              .collect(Collectors.toList());
      threadPoolService.getGeneralExecutor().submit(new LinkScanRunnable(urlList));
    }
  }

  @PostMapping("/post/restart")
  @ResponseStatus(value = HttpStatus.OK)
  public void restartPost(@RequestBody @NonNull List<PostId> postIds) {
    synchronized (LOCK) {
      downloadService.restartAll(
          postIds.stream().map(PostId::getPostId).collect(Collectors.toList()));
    }
  }

  @PostMapping("/post/add")
  @ResponseStatus(value = HttpStatus.OK)
  public void addPost(@RequestBody List<PostToAdd> posts) {
    synchronized (LOCK) {
      for (PostToAdd post : posts) {
        threadPoolService
            .getGeneralExecutor()
            .submit(new AddPostRunnable(post.getPostId(), post.getThreadId()));
      }
    }
  }

  @GetMapping("/post/path/{postId}")
  @ResponseStatus(value = HttpStatus.OK)
  public DownloadPath folderPath(@PathVariable("postId") String postId) {
    synchronized (LOCK) {
      return getDownloadPath(postId);
    }
  }

  private DownloadPath getDownloadPath(String postId) {
    Optional<Post> _post = dataService.findPostByPostId(postId);
    if (_post.isPresent()) {
      Post post = _post.get();
      if (post.getDownloadDirectory() == null) {
        log.error("Download has not been started yet for this post");
        throw new NotFoundException("Download has not been started yet for this post");
      } else {
        return new DownloadPath(
            pathService.calcDownloadDirectory(post, settingsService.getSettings()).getPath());
      }
    } else {
      log.error(String.format("Unable to find post with postId = %s", postId));
      throw new NotFoundException(String.format("Unable to find post with postId = %s", postId));
    }
  }

  @PostMapping("/post/restart/all")
  @ResponseStatus(value = HttpStatus.OK)
  public void restartPost() {
    synchronized (LOCK) {
      downloadService.restartAll(null);
    }
  }

  @PostMapping("/post/stop")
  @ResponseStatus(value = HttpStatus.OK)
  public void stop(@RequestBody @NonNull List<PostId> postIds) {
    synchronized (LOCK) {
      downloadService.stopAll(postIds.stream().map(PostId::getPostId).collect(Collectors.toList()));
    }
  }

  @PostMapping("/post/stop/all")
  @ResponseStatus(value = HttpStatus.OK)
  public void stopAll() {
    synchronized (LOCK) {
      downloadService.stopAll(null);
    }
  }

  @PostMapping("/post/remove")
  @ResponseStatus(value = HttpStatus.OK)
  public List<RemoveResult> remove(@RequestBody @NonNull List<PostId> postIds) {
    synchronized (LOCK) {
      List<RemoveResult> result = new ArrayList<>();
      List<String> collect =
          postIds.stream()
              .map(PostId::getPostId)
              .peek(e -> result.add(new RemoveResult(e)))
              .collect(Collectors.toList());
      downloadService.stopAll(collect);
      dataService.removeAll(collect);
      return result;
    }
  }

  @PostMapping("/post/rename")
  @ResponseStatus(value = HttpStatus.OK)
  public List<AltPostName> rename(@RequestBody @NonNull List<AltPostName> postToRename) {
    synchronized (LOCK) {
      renamePosts(postToRename);
      return postToRename;
    }
  }

  private void renamePosts(@RequestBody @NonNull List<AltPostName> postToRename) {
    synchronized (LOCK) {
      for (AltPostName altPostName : postToRename) {
        try {
          pathService.rename(
              altPostName.getPostId(), altPostName.getAltName(), settingsService.getSettings());
        } catch (Exception e) {
          log.error(
              String.format("Failed to rename post with postId = %s", altPostName.getPostId()), e);
          throw new ServerErrorException(e.getMessage());
        }
      }
    }
  }

  @PostMapping("/post/rename/first")
  @ResponseStatus(value = HttpStatus.OK)
  public List<PostId> renameFirst(@RequestBody @NonNull List<PostId> postToRename) {
    synchronized (LOCK) {
      renamePostsToFirst(postToRename);
      return postToRename;
    }
  }

  public void renamePostsToFirst(@RequestBody @NonNull List<PostId> postToRename) {
    synchronized (LOCK) {
      for (PostId postId : postToRename) {
        Optional<Metadata> _metadata = dataService.findMetadataByPostId(postId.getPostId());
        if (_metadata.isPresent()) {
          Metadata metadata = _metadata.get();
          if (metadata.getResolvedNames() != null) {
            List<String> resolvedNames = metadata.getResolvedNames();
            if (!resolvedNames.isEmpty()) {
              String altTitle = resolvedNames.get(0);
              try {
                pathService.rename(postId.getPostId(), altTitle, settingsService.getSettings());
              } catch (Exception e) {
                log.error(
                    String.format("Failed to rename post with postId = %s", postId.getPostId()), e);
                throw new ServerErrorException(e.getMessage());
              }
            }
          }
        }
      }
    }
  }

  @PostMapping("/post/clear/all")
  @ResponseStatus(value = HttpStatus.OK)
  public RemoveAllResult clearAll() {
    synchronized (LOCK) {
      return new RemoveAllResult(dataService.clearCompleted());
    }
  }

  @GetMapping("/grab/{threadId}")
  @ResponseStatus(value = HttpStatus.OK)
  public List<MultiPostItem> grab(@PathVariable("threadId") @NonNull String threadId) {
    synchronized (LOCK) {
      try {
        Queued queued =
            dataService
                .findQueuedByThreadId(threadId)
                .orElseThrow(
                    () ->
                        new NotFoundException(
                            String.format("Unable to find links for threadId = %s", threadId)));
        MultiPostScanResult multiPostScanResult = postService.get(queued);
        if (multiPostScanResult == null) {
          log.error(String.format("Failed to get links for threadId = %s", threadId));
          throw new ServerErrorException(
              String.format("Failed to get links for threadId = %s", threadId));
        } else {
          return multiPostScanResult.getPosts();
        }
      } catch (Exception e) {
        throw new ServerErrorException(
            String.format("Failed to get links for threadId = %s, %s", threadId, e.getMessage()));
      }
    }
  }

  @PostMapping("/grab/remove")
  @ResponseStatus(value = HttpStatus.OK)
  public ThreadId grabRemove(@RequestBody @NonNull ThreadId threadId) {
    synchronized (LOCK) {
      postService.remove(threadId.getThreadId());
      return threadId;
    }
  }

  @GetMapping("/grab/clear")
  @ResponseStatus(value = HttpStatus.OK)
  public void grabClear() {
    synchronized (LOCK) {
      dataService.clearQueueLinks();
    }
  }
}
