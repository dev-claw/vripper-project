package me.vripper.exception;

public class HostException extends Exception {
  public HostException(Throwable e) {
    super(e);
  }

  public HostException(String message) {
    super(message);
  }

  public HostException(String message, Throwable e) {
    super(message, e);
  }
}
