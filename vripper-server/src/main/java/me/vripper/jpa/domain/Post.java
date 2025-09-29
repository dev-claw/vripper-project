package me.vripper.jpa.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.vripper.jpa.domain.enums.Status;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Post {

  @JsonIgnore private Long id;

  private Status status;

  private String postId;

  private String threadTitle;

  private String threadId;

  private String title;

  private String url;

  private int done;

  private int total;

  private Set<String> hosts;

  private String forum;

  @JsonIgnore private String securityToken;

  @JsonIgnore private String downloadDirectory;

  private boolean thanked;

  private Set<String> previews = Collections.emptySet();

  private Metadata metadata;

  private int rank = Integer.MAX_VALUE;

  @JsonSerialize(using = DateTimeSerializer.class)
  private LocalDateTime addedOn;

  public Post(
      String title,
      String url,
      String postId,
      String threadId,
      String threadTitle,
      String forum,
      String securityToken) {
    this.title = title;
    this.url = url;
    this.postId = postId;
    this.forum = forum;
    this.threadId = threadId;
    this.threadTitle = threadTitle;
    this.securityToken = securityToken;
    status = Status.STOPPED;
    addedOn = LocalDateTime.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Post post = (Post) o;
    return Objects.equals(postId, post.postId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(postId);
  }
}
