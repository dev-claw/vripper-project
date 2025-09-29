package me.vripper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {

  public static String throwableToString(Throwable th) {
    try (StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter)) {
      th.printStackTrace(printWriter);
      printWriter.flush();
      stringWriter.flush();
      return stringWriter.toString();
    } catch (IOException e) {
      log.error("Failed to convert throwable to string", e);
    }
    return "Cannot display error, please check the log file for details";
  }
}
