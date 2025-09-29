package me.vripper.services.domain;

import java.util.List;
import lombok.Getter;

@Getter
public class MultiPostItem {
  private final String threadId;
  private final String postId;
  private final int number;
  private final String title;
  private final int imageCount;
  private final String url;
  private final List<String> previews;
  private final String hosts;

  public MultiPostItem(
      String threadId,
      String postId,
      int number,
      String title,
      int imageCount,
      String url,
      List<String> previews,
      String hosts) {
    this.threadId = threadId;
    this.postId = postId;
    this.number = number;
    this.title = title;
    this.imageCount = imageCount;
    this.previews = previews;
    this.url = url;
    this.hosts = hosts;
  }
}
