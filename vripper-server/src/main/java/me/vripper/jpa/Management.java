package me.vripper.jpa;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.vripper.download.DownloadService;
import me.vripper.services.ThreadPoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class Management {

  public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final Pattern BACKUP_FILE_PATTERN =
      Pattern.compile("^db_(\\d{4}-\\d{2}-\\d{2})\\.tar\\.gz$");

  private final JdbcTemplate jdbcTemplate;
  private final String backupFolder;
  private final ThreadPoolService threadPoolService;
  private final DownloadService downloadService;

  public Management(
      JdbcTemplate jdbcTemplate,
      @Value("${base.dir}") String baseDir,
      @Value("${base.dir.name}") String baseDirName,
      ThreadPoolService threadPoolService,
      DownloadService downloadService) {
    this.jdbcTemplate = jdbcTemplate;
    this.threadPoolService = threadPoolService;
    this.downloadService = downloadService;
    this.backupFolder = baseDir + File.separator + baseDirName + File.separator + "backup";
  }

  @PreDestroy
  public void destroy() {

    try {
      threadPoolService.destroy();
      downloadService.destroy();
      this.jdbcTemplate.execute("SHUTDOWN");
    } catch (Exception ignored) {
      Thread.currentThread().interrupt();
    }
  }

  @PostConstruct
  @Scheduled(cron = "0 0 0 ? * *")
  private void backup() {

    for (File file :
        Optional.ofNullable(new File(backupFolder).listFiles()).orElse(new File[] {})) {
      Matcher matcher = BACKUP_FILE_PATTERN.matcher(file.getName());
      if (matcher.find()) {
        LocalDate localDate = LocalDate.parse(matcher.group(1), FORMATTER);
        if (localDate.isEqual(LocalDate.now())) {
          return;
        }
      }
    }

    // create a backup file
    File backupFile =
        new File(
            backupFolder, String.format("db_%s.tar.gz", LocalDateTime.now().format(FORMATTER)));
    jdbcTemplate.execute(String.format("BACKUP DATABASE TO '%s' BLOCKING", backupFile.getPath()));
  }
}
