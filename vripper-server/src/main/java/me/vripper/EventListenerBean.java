package me.vripper;

import lombok.Getter;
import me.vripper.jpa.repositories.IPostRepository;
import me.vripper.services.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EventListenerBean {

  @Getter private static boolean init = false;

  private final IPostRepository postRepository;
  private final DataService dataService;

  @Autowired
  public EventListenerBean(IPostRepository postRepository, DataService dataService) {
    this.postRepository = postRepository;
    this.dataService = dataService;
  }

  @EventListener
  public void onApplicationEvent(ContextRefreshedEvent event) {
    postRepository.setDownloadingToStopped();
    dataService.sortPostsByRank();
    init = true;
  }
}
