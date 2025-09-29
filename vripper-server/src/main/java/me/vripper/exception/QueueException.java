package me.vripper.exception;

public class QueueException extends Exception {

  public QueueException(String message) {
    super(message);
  }

  public QueueException(String message, Throwable e) {
    super(message, e);
  }

  public QueueException(Throwable e) {
    super(e);
  }
}
