package me.vripper.exception;

public class PostParseException extends Exception {

  public PostParseException(String message) {
    super(message);
  }

  public PostParseException(String message, Throwable e) {
    super(message, e);
  }

  public PostParseException(Throwable e) {
    super(e);
  }
}
