package me.vripper.event;

import lombok.Getter;

@Getter
public class Event<T> {

  private final T data;
  private final Kind kind;

  private Event(Kind kind, T data) {
    this.data = data;
    this.kind = kind;
  }

  public static <T> Event<T> wrap(Kind kind, T data) {
    return new Event<>(kind, data);
  }

  public enum Kind {
    POST_UPDATE,
    POST_REMOVE,
    IMAGE_UPDATE,
    METADATA_UPDATE,
    QUEUED_UPDATE,
    QUEUED_REMOVE,
    LOG_EVENT_UPDATE,
    LOG_EVENT_REMOVE,
    VG_USER,
    GLOBAL_STATE,
    BYTES_PER_SECOND,
    SETTINGS_UPDATE
  }
}
