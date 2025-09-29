package me.vripper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class VripperApplication {

  public static void main(String[] args) {
    try {
      Runtime.getRuntime().addShutdownHook(new Thread(SpringContext::close));
      SpringApplication.run(VripperApplication.class, args);
    } catch (Exception e) {
      log.error("Failed to run the application", e);
    }
  }
}
