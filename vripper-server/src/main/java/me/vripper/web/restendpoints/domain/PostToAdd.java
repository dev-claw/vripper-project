package me.vripper.web.restendpoints.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class PostToAdd {
  private String threadId;
  private String postId;
}
