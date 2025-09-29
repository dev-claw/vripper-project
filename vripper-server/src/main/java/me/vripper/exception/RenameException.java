package me.vripper.exception;

import java.io.IOException;

public class RenameException extends Exception {

  public RenameException(String message, IOException e) {
    super(message, e);
  }
}
