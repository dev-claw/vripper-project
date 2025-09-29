package me.vripper.exception;

public class DownloadException extends Exception {

  public DownloadException(String message) {
    super(message);
  }

  public DownloadException(Throwable e) {
    super(e);
  }
}
