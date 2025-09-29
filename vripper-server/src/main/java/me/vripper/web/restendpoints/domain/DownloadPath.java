package me.vripper.web.restendpoints.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class DownloadPath {
  private String path;

  public DownloadPath(String path) {
    this.path = path;
  }
}
