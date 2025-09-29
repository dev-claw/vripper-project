package me.vripper.tasks;

import static me.vripper.jpa.domain.LogEvent.Status.ERROR;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.Utils;
import me.vripper.jpa.domain.LogEvent;
import me.vripper.jpa.domain.Queued;
import me.vripper.jpa.repositories.impl.LogEventRepository;
import me.vripper.services.PostService;
import me.vripper.services.SettingsService;
import org.springframework.lang.NonNull;

@Slf4j
public class LinkScanRunnable implements Runnable {

  private static final Object LOCK = new Object();
  private final List<String> urlList;
  private final SettingsService settingsService;
  private final PostService postService;
  private final LogEventRepository eventRepository;
  private final LogEvent logEvent;

  public LinkScanRunnable(@NonNull List<String> urlList) {
    this.urlList = urlList;
    settingsService = SpringContext.getBean(SettingsService.class);
    postService = SpringContext.getBean(PostService.class);
    eventRepository = SpringContext.getBean(LogEventRepository.class);
    logEvent =
        new LogEvent(
            LogEvent.Type.SCAN,
            LogEvent.Status.PENDING,
            LocalDateTime.now(),
            "Links to scan:\n\t" + String.join("\n\t", urlList));
    eventRepository.save(logEvent);
  }

  @Override
  public void run() {
    synchronized (LOCK) {
      try {
        logEvent.setStatus(LogEvent.Status.PROCESSING);
        eventRepository.update(logEvent);
        ArrayList<Queued> queuedList = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>();
        for (String url : urlList) {
          log.debug(String.format("Starting to process thread: %s", url));
          if (!url.startsWith(settingsService.getSettings().getVProxy())) {
            log.error(String.format("Unsupported link %s", url));
            unsupported.add(url);
            continue;
          }

          String threadId, postId;
          Matcher m =
              Pattern.compile(
                      Pattern.quote(settingsService.getSettings().getVProxy())
                          + "/threads/(\\d+)((.*p=)(\\d+))?")
                  .matcher(url);
          if (m.find()) {
            threadId = m.group(1);
            postId = m.group(4);
          } else {
            log.error(String.format("Cannot retrieve thread id from URL %s", url));
            unrecognized.add(url);
            continue;
          }
          queuedList.add(new Queued(url, threadId, postId));
        }
        StringBuilder errorMessage = new StringBuilder();
        if (!unsupported.isEmpty()) {
          errorMessage
              .append("Unsupported links:\n\t")
              .append(String.join("\n\t", unsupported))
              .append("\n\n");
        }
        if (!unrecognized.isEmpty()) {
          errorMessage
              .append("Unrecognized links:\n\t")
              .append(String.join("\n\t", unrecognized))
              .append("\n\n");
        }

        postService.processMultiPost(queuedList);
        if (!unsupported.isEmpty() || !unrecognized.isEmpty()) {
          logEvent.setStatus(LogEvent.Status.ERROR);
          logEvent.setMessage("Some links failed to be scanned: \n" + errorMessage);
        } else {
          logEvent.setStatus(LogEvent.Status.DONE);
        }
        eventRepository.update(logEvent);
      } catch (Exception e) {
        String error = "Error when scanning links";
        log.error(error, e);
        logEvent.setMessage(error + "\n" + Utils.throwableToString(e));
        logEvent.setStatus(ERROR);
        eventRepository.update(logEvent);
      }
    }
  }
}
