package me.vripper.web.restendpoints.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class AltPostName {
  private String postId;
  private String altName;
}
