package me.vripper.services.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Settings implements Cloneable {

  @JsonProperty("downloadPath")
  private String downloadPath;

  @JsonProperty("maxThreads")
  private Integer maxThreads;

  @JsonProperty("maxTotalThreads")
  private Integer maxTotalThreads;

  @JsonProperty("autoStart")
  private Boolean autoStart;

  @JsonProperty("vLogin")
  private Boolean vLogin;

  @JsonProperty("vUsername")
  private String vUsername;

  @JsonProperty("vPassword")
  private String vPassword;

  @JsonProperty("vThanks")
  private Boolean vThanks;

  @JsonProperty("desktopClipboard")
  private Boolean desktopClipboard;

  @JsonProperty("forceOrder")
  private Boolean forceOrder;

  @JsonProperty("subLocation")
  private Boolean subLocation;

  @JsonProperty("threadSubLocation")
  private Boolean threadSubLocation;

  @JsonProperty("clearCompleted")
  private Boolean clearCompleted;

  @JsonProperty("darkTheme")
  private Boolean darkTheme;

  @JsonProperty("appendPostId")
  private Boolean appendPostId;

  @JsonProperty("leaveThanksOnStart")
  private Boolean leaveThanksOnStart;

  @JsonProperty("connectionTimeout")
  private Integer connectionTimeout;

  @JsonProperty("maxAttempts")
  private Integer maxAttempts;

  @JsonProperty("vProxy")
  private String vProxy;

  @JsonProperty("maxEventLog")
  private Integer maxEventLog;

  @Override
  public Object clone() {
    Settings clone;
    try {
      clone = (Settings) super.clone();
    } catch (CloneNotSupportedException e) {
      log.error(e.getMessage(), e);
      clone = new Settings();
    }
    clone.downloadPath = downloadPath;
    clone.maxThreads = maxThreads;
    clone.maxTotalThreads = maxTotalThreads;
    clone.autoStart = autoStart;
    clone.vLogin = vLogin;
    clone.vUsername = vUsername;
    clone.vPassword = vPassword;
    clone.vThanks = vThanks;
    clone.desktopClipboard = desktopClipboard;
    clone.forceOrder = forceOrder;
    clone.subLocation = subLocation;
    clone.threadSubLocation = threadSubLocation;
    clone.clearCompleted = clearCompleted;
    clone.darkTheme = darkTheme;
    clone.appendPostId = appendPostId;
    clone.leaveThanksOnStart = leaveThanksOnStart;
    clone.connectionTimeout = connectionTimeout;
    clone.maxAttempts = maxAttempts;
    clone.vProxy = vProxy;
    clone.maxEventLog = maxEventLog;
    return clone;
  }
}
