package me.vripper.jpa.repositories.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import me.vripper.jpa.domain.LogEvent;
import me.vripper.jpa.repositories.ILogEventRepository;
import me.vripper.services.SettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LogEventRepository implements ILogEventRepository {

  private final JdbcTemplate jdbcTemplate;
  private final SettingsService settingsService;
  private final EventBus eventBus;

  public LogEventRepository(
      JdbcTemplate jdbcTemplate, SettingsService settingsService, EventBus eventBus) {
    this.jdbcTemplate = jdbcTemplate;
    this.settingsService = settingsService;
    this.eventBus = eventBus;
  }

  private synchronized Long nextId() {
    return jdbcTemplate.queryForObject("CALL NEXT VALUE FOR SEQ_EVENT", Long.class);
  }

  @Override
  public synchronized LogEvent save(@NonNull LogEvent logEvent) {

    int maxRecords = settingsService.getSettings().getMaxEventLog() - 1;

    Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM EVENT", Long.class);
    if (count > maxRecords) {
      List<Long> idList =
          jdbcTemplate.queryForList(
              "SELECT ID FROM EVENT ORDER BY TIME ASC LIMIT ?", Long.class, count - maxRecords);
      idList.forEach(this::delete);
    }

    long id = nextId();
    jdbcTemplate.update(
        "INSERT INTO EVENT (ID, TYPE, STATUS, TIME, MESSAGE) VALUES (?,?,?,?,?)",
        id,
        logEvent.getType().name(),
        logEvent.getStatus().name(),
        logEvent.getTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        logEvent.getMessage());
    logEvent.setId(id);
    eventBus.publishEvent(Event.wrap(Event.Kind.LOG_EVENT_UPDATE, id));
    return logEvent;
  }

  @Override
  public LogEvent update(@NonNull LogEvent logEvent) {
    if (logEvent.getId() == null) {
      log.warn("Cannot update entity with null id");
      return logEvent;
    }

    jdbcTemplate.update(
        "UPDATE EVENT SET TYPE = ?, STATUS = ?, TIME = ?, MESSAGE = ? WHERE ID = ?",
        logEvent.getType().name(),
        logEvent.getStatus().name(),
        logEvent.getTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        logEvent.getMessage(),
        logEvent.getId());
    eventBus.publishEvent(Event.wrap(Event.Kind.LOG_EVENT_UPDATE, logEvent.getId()));
    return logEvent;
  }

  @Override
  public Optional<LogEvent> findById(Long id) {
    List<LogEvent> logEvents =
        jdbcTemplate.query("SELECT * FROM EVENT WHERE ID = ?", new LogEventRowMapper(), id);
    if (logEvents.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(logEvents.get(0));
    }
  }

  @Override
  public List<LogEvent> findAll() {
    return jdbcTemplate.query("SELECT * FROM EVENT", new LogEventRowMapper());
  }

  @Override
  public void delete(Long id) {
    jdbcTemplate.update("DELETE FROM EVENT WHERE ID = ?", id);
    eventBus.publishEvent(Event.wrap(Event.Kind.LOG_EVENT_REMOVE, id));
  }

  @Override
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM EVENT");
  }
}

class LogEventRowMapper implements RowMapper<LogEvent> {

  @Override
  public LogEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
    LogEvent logEvent = new LogEvent();
    logEvent.setId(rs.getLong("ID"));
    logEvent.setType(LogEvent.Type.valueOf(rs.getString("TYPE")));
    logEvent.setStatus(LogEvent.Status.valueOf(rs.getString("STATUS")));
    logEvent.setTime(
        LocalDateTime.parse(rs.getString("TIME"), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    logEvent.setMessage(rs.getString("MESSAGE"));
    return logEvent;
  }
}
