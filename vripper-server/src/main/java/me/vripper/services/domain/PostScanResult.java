package me.vripper.services.domain;

import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import me.vripper.jpa.domain.Image;
import me.vripper.jpa.domain.Post;

public class PostScanResult {

  private final Post post;

  @Getter private final Set<Image> images;

  public PostScanResult(Post post, Set<Image> images) {
    this.post = post;
    this.images = images;
  }

  public Optional<Post> getPost() {
    return Optional.ofNullable(post);
  }
}
