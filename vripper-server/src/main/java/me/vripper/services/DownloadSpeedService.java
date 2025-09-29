package me.vripper.services;

import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class DownloadSpeedService {

  private final AtomicLong read = new AtomicLong(0);
  private final EventBus eventBus;
  @Getter private long currentValue;
  private boolean allowWrite = false;

  public DownloadSpeedService(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void increase(long read) {
    if (allowWrite) {
      this.read.addAndGet(read);
    }
  }

  @Scheduled(fixedDelay = 1000)
  private void calc() {
    allowWrite = false;
    long newValue = read.getAndSet(0);
    if (newValue != currentValue) {
      currentValue = newValue;
      eventBus.publishEvent(Event.wrap(Event.Kind.BYTES_PER_SECOND, currentValue));
    }
    allowWrite = true;
  }
}
