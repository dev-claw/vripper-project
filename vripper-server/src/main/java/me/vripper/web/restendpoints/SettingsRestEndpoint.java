package me.vripper.web.restendpoints;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import me.vripper.exception.ValidationException;
import me.vripper.services.SettingsService;
import me.vripper.services.domain.Settings;
import me.vripper.web.restendpoints.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@CrossOrigin(value = "*")
public class SettingsRestEndpoint {

  private final SettingsService settingsService;

  @Autowired
  public SettingsRestEndpoint(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  @PostMapping("/settings/theme")
  @ResponseStatus(value = HttpStatus.OK)
  public SettingsService.Theme postTheme(@RequestBody SettingsService.Theme theme) {
    this.settingsService.setTheme(theme);
    return settingsService.getTheme();
  }

  @GetMapping("/settings/theme")
  @ResponseStatus(value = HttpStatus.OK)
  public SettingsService.Theme getTheme() {
    return settingsService.getTheme();
  }

  @PostMapping("/settings")
  @ResponseStatus(value = HttpStatus.OK)
  public Settings postSettings(@RequestBody Settings settings) {

    try {
      this.settingsService.check(settings);
    } catch (ValidationException e) {
      log.error("Invalid settings", e);
      throw new BadRequestException(e.getMessage());
    }

    this.settingsService.newSettings(settings);
    return getAppSettingsService();
  }

  @GetMapping("/settings")
  @ResponseStatus(value = HttpStatus.OK)
  public Settings getAppSettingsService() {

    return settingsService.getSettings();
  }

  @GetMapping("/settings/proxies")
  @ResponseStatus(value = HttpStatus.OK)
  public List<String> mirrors() {
    return settingsService.getProxies();
  }
}
