package me.vripper.jpa.repositories.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import me.vripper.jpa.domain.Queued;
import me.vripper.jpa.repositories.IQueuedRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class QueuedRepository implements IQueuedRepository {

  private final JdbcTemplate jdbcTemplate;
  private final EventBus eventBus;

  @Autowired
  public QueuedRepository(JdbcTemplate jdbcTemplate, EventBus eventBus) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventBus = eventBus;
  }

  private synchronized Long nextId() {
    return jdbcTemplate.queryForObject("CALL NEXT VALUE FOR SEQ_QUEUED", Long.class);
  }

  @Override
  public Queued save(Queued queued) {
    long id = nextId();
    jdbcTemplate.update(
        "INSERT INTO QUEUED (ID, TOTAL, LINK, LOADING, POST_ID, THREAD_ID) values (?,?,?,?,?,?)",
        id,
        queued.getTotal(),
        queued.getLink(),
        queued.isLoading(),
        queued.getPostId(),
        queued.getThreadId());
    queued.setId(id);
    eventBus.publishEvent(Event.wrap(Event.Kind.QUEUED_UPDATE, id));
    return queued;
  }

  @Override
  public Optional<Queued> findByThreadId(String threadId) {
    List<Queued> queuedList =
        jdbcTemplate.query(
            "SELECT * FROM QUEUED AS queued WHERE queued.THREAD_ID = ?",
            new QueuedRowMapper(),
            threadId);
    if (queuedList.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(queuedList.get(0));
    }
  }

  @Override
  public List<Queued> findAll() {
    return jdbcTemplate.query("SELECT * FROM QUEUED", new QueuedRowMapper());
  }

  @Override
  public Optional<Queued> findById(Long id) {
    List<Queued> queuedList =
        jdbcTemplate.query(
            "SELECT * FROM QUEUED AS queued WHERE queued.ID = ?", new QueuedRowMapper(), id);
    if (queuedList.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(queuedList.get(0));
    }
  }

  @Override
  public int deleteByThreadId(String threadId) {
    int mutationCount =
        jdbcTemplate.update("DELETE FROM QUEUED AS queued WHERE THREAD_ID = ?", threadId);
    eventBus.publishEvent(Event.wrap(Event.Kind.QUEUED_REMOVE, threadId));
    return mutationCount;
  }

  @Override
  public void deleteAll() {
    jdbcTemplate.update("DELETE FROM QUEUED");
  }
}

class QueuedRowMapper implements RowMapper<Queued> {

  @Override
  public Queued mapRow(ResultSet rs, int rowNum) throws SQLException {
    Queued queued = new Queued();
    queued.setId(rs.getLong("ID"));
    queued.setLink(rs.getString("LINK"));
    queued.setThreadId(rs.getString("THREAD_ID"));
    queued.setPostId(rs.getString("POST_ID"));
    queued.setTotal(rs.getInt("TOTAL"));
    queued.setLoading(rs.getBoolean("LOADING"));
    return queued;
  }
}
