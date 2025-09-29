package tn.mnlr.vripper.services;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import tn.mnlr.vripper.event.Event;
import tn.mnlr.vripper.event.EventBus;
import tn.mnlr.vripper.jpa.domain.Queued;
import tn.mnlr.vripper.services.domain.MultiPostScanParser;
import tn.mnlr.vripper.services.domain.MultiPostScanResult;
import tn.mnlr.vripper.tasks.AddPostRunnable;
import tn.mnlr.vripper.tasks.AddQueuedRunnable;

@Service
@Slf4j
public class PostService {

  private final DataService dataService;
  private final ThreadPoolService threadPoolService;
  private final LoadingCache<Queued, MultiPostScanResult> cache;
  private final Disposable disposable;

  @Autowired
  public PostService(
      DataService dataService, ThreadPoolService threadPoolService, EventBus eventBus) {
    this.dataService = dataService;
    this.threadPoolService = threadPoolService;
    cache =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(multiPostItem -> new MultiPostScanParser(multiPostItem).parse());
    disposable =
        eventBus
            .flux()
            .filter(e -> e.getKind().equals(Event.Kind.SETTINGS_UPDATE))
            .subscribe(e -> this.cache.invalidateAll());
  }

  @PreDestroy
  private void destroy() {
    if (disposable != null) {
      disposable.dispose();
    }
  }

  public void processMultiPost(List<Queued> queuedList) {
    for (Queued queued : queuedList) {
      if (queued.getPostId() != null) {
        threadPoolService
            .getGeneralExecutor()
            .submit(new AddPostRunnable(queued.getPostId(), queued.getThreadId()));
      } else {
        threadPoolService.getGeneralExecutor().submit(new AddQueuedRunnable(queued));
      }
    }
  }

  public MultiPostScanResult get(Queued queued) throws ExecutionException {
    return cache.get(queued);
  }

  public void remove(String threadId) {
    dataService.removeQueueLink(threadId);
  }
}
