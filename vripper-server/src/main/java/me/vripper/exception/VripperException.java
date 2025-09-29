package me.vripper.exception;

public class VripperException extends Exception {
  public VripperException(String message) {
    super(message);
  }

  public VripperException(Throwable e) {
    super(e);
  }
}
