package me.vripper.services.domain;

import java.util.List;
import lombok.Getter;

@Getter
public class MultiPostScanResult {
  private final List<MultiPostItem> posts;
  private final String error;

  public MultiPostScanResult(List<MultiPostItem> posts, String error) {
    this.posts = posts;
    this.error = error;
  }
}
