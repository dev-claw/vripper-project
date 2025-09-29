package me.vripper.jpa.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class Queued {

  @JsonIgnore private Long id;

  private String link;

  private String threadId;

  private String postId;

  private int total = 0;

  private boolean loading = true;

  public Queued(String link, String threadId, String postId) {
    this();
    this.link = link;
    this.threadId = threadId;
    this.postId = postId;
  }

  public void done() {
    this.loading = false;
  }

  public void increment() {
    this.total++;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Queued that = (Queued) o;
    return Objects.equals(threadId, that.threadId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(threadId);
  }
}
