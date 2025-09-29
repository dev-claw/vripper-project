package me.vripper.services;

import lombok.Getter;
import me.vripper.download.DownloadService;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import me.vripper.services.domain.GlobalState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class GlobalStateService {

  private final DownloadService downloadService;
  private final DataService dataService;
  private final EventBus eventBus;
  @Getter private GlobalState currentState;

  @Autowired
  public GlobalStateService(
      DownloadService downloadService, DataService dataService, EventBus eventBus) {
    this.downloadService = downloadService;
    this.dataService = dataService;
    this.eventBus = eventBus;
  }

  @Scheduled(fixedDelay = 3000)
  private void interval() {
    GlobalState newGlobalState =
        new GlobalState(
            downloadService.runningCount(),
            downloadService.pendingCount(),
            dataService.countErrorImages());
    if (!newGlobalState.equals(currentState)) {
      currentState = newGlobalState;
      eventBus.publishEvent(Event.wrap(Event.Kind.GLOBAL_STATE, currentState));
    }
  }
}
