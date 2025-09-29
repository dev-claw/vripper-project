package me.vripper.tasks;

import static me.vripper.jpa.domain.LogEvent.Status.ERROR;
import static me.vripper.jpa.domain.LogEvent.Status.PROCESSING;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.Utils;
import me.vripper.download.DownloadService;
import me.vripper.exception.PostParseException;
import me.vripper.jpa.domain.Image;
import me.vripper.jpa.domain.LogEvent;
import me.vripper.jpa.domain.Post;
import me.vripper.jpa.domain.enums.Status;
import me.vripper.jpa.repositories.ILogEventRepository;
import me.vripper.services.DataService;
import me.vripper.services.MetadataService;
import me.vripper.services.SettingsService;
import me.vripper.services.VGAuthService;
import me.vripper.services.domain.PostScanParser;
import me.vripper.services.domain.PostScanResult;

@Slf4j
public class AddPostRunnable implements Runnable {

  private final String postId;
  private final String threadId;
  private final DataService dataService;
  private final MetadataService metadataService;
  private final SettingsService settingsService;
  private final VGAuthService VGAuthService;
  private final LogEvent logEvent;
  private final ILogEventRepository eventRepository;
  private final String link;
  private final DownloadService downloadService;

  public AddPostRunnable(String postId, String threadId) {
    this.postId = postId;
    this.threadId = threadId;
    this.dataService = SpringContext.getBean(DataService.class);
    this.metadataService = SpringContext.getBean(MetadataService.class);
    this.settingsService = SpringContext.getBean(SettingsService.class);
    this.downloadService = SpringContext.getBean(DownloadService.class);
    this.VGAuthService = SpringContext.getBean(VGAuthService.class);
    this.eventRepository = SpringContext.getBean(ILogEventRepository.class);
    link =
        settingsService.getSettings().getVProxy()
            + String.format("/threads/%s?%s", threadId, (postId != null ? "p=" + postId : ""));
    logEvent =
        new LogEvent(
            LogEvent.Type.POST,
            LogEvent.Status.PENDING,
            LocalDateTime.now(),
            String.format("Processing %s", link));
    eventRepository.save(logEvent);
  }

  @Override
  public void run() {

    try {
      logEvent.setStatus(PROCESSING);
      eventRepository.update(logEvent);
      if (dataService.exists(postId)) {
        log.warn(String.format("skipping %s, already loaded", postId));
        logEvent.setMessage(String.format("Gallery %s is already loaded", link));
        logEvent.setStatus(ERROR);
        eventRepository.update(logEvent);
        return;
      }

      PostScanParser postScanParser = new PostScanParser(threadId, postId);

      PostScanResult postScanResult;
      try {
        postScanResult = postScanParser.parse();
      } catch (PostParseException e) {
        String error = String.format("parsing failed for gallery %s", link);
        log.error(error, e);
        logEvent.setMessage(error + "\n" + Utils.throwableToString(e));
        logEvent.setStatus(ERROR);
        eventRepository.update(logEvent);
        return;
      }
      if (postScanResult.getPost().isEmpty()) {
        String error = String.format("Gallery %s contains no galleries", link);
        log.error(error);
        logEvent.setMessage(error);
        logEvent.setStatus(ERROR);
        eventRepository.update(logEvent);
        return;
      }
      if (postScanResult.getImages().isEmpty()) {
        String error = String.format("Gallery %s contains no images to download", link);
        log.error(error);
        logEvent.setMessage(error);
        logEvent.setStatus(ERROR);
        eventRepository.update(logEvent);
        return;
      }

      Post post = postScanResult.getPost().get();
      Set<Image> images = postScanResult.getImages();

      dataService.newPost(post, images);
      metadataService.startFetchingMetadata(post);

      if (settingsService.getSettings().getAutoStart()) {
        log.debug("Auto start downloads option is enabled");
        post.setStatus(Status.PENDING);
        downloadService.enqueue(Map.of(post, images));
        log.debug(String.format("Done enqueuing jobs for %s", post.getUrl()));
      } else {
        post.setStatus(Status.STOPPED);
        log.debug("Auto start downloads option is disabled");
      }
      if (settingsService.getSettings().getLeaveThanksOnStart() != null
          && !settingsService.getSettings().getLeaveThanksOnStart()) {
        VGAuthService.leaveThanks(post);
      }
      dataService.updatePostStatus(post.getStatus(), post.getId());
      logEvent.setMessage(
          String.format("Gallery %s is successfully added to download queue", link));
      logEvent.setStatus(LogEvent.Status.DONE);
      eventRepository.update(logEvent);
    } catch (Exception e) {
      String error = String.format("Error when adding gallery %s", link);
      log.error(error, e);
      logEvent.setMessage(error + "\n" + Utils.throwableToString(e));
      logEvent.setStatus(ERROR);
      eventRepository.update(logEvent);
    }
  }
}
