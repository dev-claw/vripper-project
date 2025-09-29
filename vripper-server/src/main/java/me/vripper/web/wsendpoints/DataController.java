package me.vripper.web.wsendpoints;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import me.vripper.jpa.domain.Image;
import me.vripper.jpa.domain.LogEvent;
import me.vripper.jpa.domain.Post;
import me.vripper.jpa.domain.Queued;
import me.vripper.services.DataService;
import me.vripper.services.DownloadSpeedService;
import me.vripper.services.GlobalStateService;
import me.vripper.services.VGAuthService;
import me.vripper.services.domain.DownloadSpeed;
import me.vripper.services.domain.GlobalState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class DataController {

  private final VGAuthService VGAuthService;
  private final GlobalStateService globalStateService;
  private final DownloadSpeedService downloadSpeedService;
  private final DataService dataService;

  @Autowired
  public DataController(
      VGAuthService VGAuthService,
      GlobalStateService globalStateService,
      DownloadSpeedService downloadSpeedService,
      DataService dataService) {
    this.VGAuthService = VGAuthService;
    this.globalStateService = globalStateService;
    this.downloadSpeedService = downloadSpeedService;
    this.dataService = dataService;
  }

  @SubscribeMapping("/user")
  public LoggedUser user() {
    return new LoggedUser(VGAuthService.getLoggedUser());
  }

  @SubscribeMapping("/download-state")
  public GlobalState downloadState() {
    return globalStateService.getCurrentState();
  }

  @SubscribeMapping("/speed")
  public DownloadSpeed speed() {
    return new DownloadSpeed(downloadSpeedService.getCurrentValue());
  }

  @SubscribeMapping("/posts")
  public Collection<Post> posts() {
    List<Post> posts = dataService.findAllPosts();
    posts.sort(Comparator.comparing(Post::getAddedOn));
    return posts;
  }

  @SubscribeMapping("/images/{postId}")
  public List<Image> postsDetails(@DestinationVariable("postId") String postId) {
    return dataService.findImagesByPostId(postId);
  }

  @SubscribeMapping("/queued")
  public Collection<Queued> queued() {
    return dataService.findAllQueued();
  }

  @SubscribeMapping("/events")
  public Collection<LogEvent> events() {
    return dataService.findAllEvents();
  }

  @Getter
  public static class LoggedUser {

    private final String user;

    LoggedUser(String user) {
      this.user = user;
    }
  }
}
