package me.vripper.web.restendpoints.domain;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class RemoveAllResult {
  private List<String> postIds;
  private int removed;

  public RemoveAllResult(List<String> postIds) {
    this.removed = postIds.size();
    this.postIds = postIds;
  }
}
