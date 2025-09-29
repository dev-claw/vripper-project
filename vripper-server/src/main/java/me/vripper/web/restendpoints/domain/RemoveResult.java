package me.vripper.web.restendpoints.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class RemoveResult {
  private String postId;

  public RemoveResult(String postId) {
    this.postId = postId;
  }
}
