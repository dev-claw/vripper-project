package me.vripper.tasks;

import static me.vripper.jpa.domain.LogEvent.Status.ERROR;
import static me.vripper.jpa.domain.LogEvent.Status.PROCESSING;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.Utils;
import me.vripper.jpa.domain.LogEvent;
import me.vripper.jpa.domain.Queued;
import me.vripper.jpa.repositories.ILogEventRepository;
import me.vripper.services.DataService;
import me.vripper.services.PostService;
import me.vripper.services.ThreadPoolService;
import me.vripper.services.domain.MultiPostScanResult;

@Slf4j
public class AddQueuedRunnable implements Runnable {

  private final Queued queued;
  private final ThreadPoolService threadPoolService;
  private final DataService dataService;
  private final ILogEventRepository eventRepository;
  private final PostService postService;
  private final LogEvent logEvent;

  public AddQueuedRunnable(Queued queued) {
    this.queued = queued;
    threadPoolService = SpringContext.getBean(ThreadPoolService.class);
    dataService = SpringContext.getBean(DataService.class);
    eventRepository = SpringContext.getBean(ILogEventRepository.class);
    postService = SpringContext.getBean(PostService.class);
    logEvent =
        new LogEvent(
            LogEvent.Type.QUEUED,
            LogEvent.Status.PENDING,
            LocalDateTime.now(),
            String.format("Processing multi-post link %s", queued.getLink()));
    eventRepository.save(logEvent);
  }

  @Override
  public void run() {
    try {
      logEvent.setStatus(PROCESSING);
      eventRepository.update(logEvent);
      MultiPostScanResult multiPostScanResult = postService.get(queued);
      if (multiPostScanResult == null) {
        String message = String.format("Fetching multi-post link %s failed", queued.getLink());
        logEvent.setStatus(ERROR);
        logEvent.setMessage(message);
        eventRepository.update(logEvent);
        return;
      } else if (multiPostScanResult.getError() != null) {
        String message =
            "Nothing found for " + queued.getLink() + "\n" + multiPostScanResult.getError();
        logEvent.setStatus(ERROR);
        logEvent.setMessage(message);
        eventRepository.update(logEvent);
        return;
      } else if (multiPostScanResult.getPosts().isEmpty()) {
        String message = "Nothing found for " + queued.getLink();
        logEvent.setStatus(ERROR);
        logEvent.setMessage(message);
        eventRepository.update(logEvent);
        return;
      }
      queued.setTotal(multiPostScanResult.getPosts().size());
      queued.done();
      if (multiPostScanResult.getPosts().size() == 1) {
        threadPoolService
            .getGeneralExecutor()
            .submit(
                new AddPostRunnable(
                    multiPostScanResult.getPosts().get(0).getPostId(),
                    multiPostScanResult.getPosts().get(0).getThreadId()));
        logEvent.setStatus(LogEvent.Status.DONE);
        logEvent.setMessage(String.format("Link %s is added to download queue", queued.getLink()));
      } else {
        if (dataService.findQueuedByThreadId(queued.getThreadId()).isEmpty()) {
          dataService.newQueueLink(queued);
          logEvent.setStatus(LogEvent.Status.DONE);
          logEvent.setMessage(
              String.format("Link %s is added to multi-post links", queued.getLink()));
        } else {
          log.info(String.format("Link %s is already loaded", queued.getLink()));
          logEvent.setStatus(LogEvent.Status.ERROR);
          logEvent.setMessage(
              String.format("%s has already been added to multi-post links", queued.getLink()));
        }
      }
      eventRepository.update(logEvent);
    } catch (Exception e) {
      String error = String.format("Error when adding multi-post link %s", queued.getLink());
      log.error(error, e);
      logEvent.setMessage(error + "\n" + Utils.throwableToString(e));
      logEvent.setStatus(ERROR);
      eventRepository.update(logEvent);
    }
  }
}
