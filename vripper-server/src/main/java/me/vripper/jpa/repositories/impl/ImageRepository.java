package me.vripper.jpa.repositories.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import me.vripper.SpringContext;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import me.vripper.host.Host;
import me.vripper.jpa.domain.Image;
import me.vripper.jpa.domain.enums.Status;
import me.vripper.jpa.repositories.IImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class ImageRepository implements IImageRepository {

  private final JdbcTemplate jdbcTemplate;
  private final EventBus eventBus;

  @Autowired
  public ImageRepository(JdbcTemplate jdbcTemplate, EventBus eventBus) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventBus = eventBus;
  }

  private synchronized Long nextId() {
    return jdbcTemplate.queryForObject("CALL NEXT VALUE FOR SEQ_IMAGE", Long.class);
  }

  @Override
  public Image save(Image image) {
    long id = nextId();
    jdbcTemplate.update(
        "INSERT INTO IMAGE (ID, CURRENT, HOST, INDEX, POST_ID, STATUS, TOTAL, URL, THUMB_URL, POST_ID_REF) VALUES (?,?,?,?,?,?,?,?,?,?)",
        id,
        image.getCurrent(),
        image.getHost().getHost(),
        image.getIndex(),
        image.getPostId(),
        image.getStatus().name(),
        image.getTotal(),
        image.getUrl(),
        image.getThumbUrl(),
        image.getPostIdRef());
    image.setId(id);
    eventBus.publishEvent(Event.wrap(Event.Kind.IMAGE_UPDATE, id));
    return image;
  }

  @Override
  public void deleteAllByPostId(String postId) {
    jdbcTemplate.update("DELETE FROM IMAGE WHERE POST_ID = ?", postId);
  }

  @Override
  public List<Image> findByPostId(String postId) {
    return jdbcTemplate.query(
        "SELECT * FROM IMAGE WHERE POST_ID = ?", new ImageRowMapper(), postId);
  }

  @Override
  public Integer countError() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM IMAGE AS image WHERE image.STATUS = 'ERROR'", Integer.class);
  }

  @Override
  public List<Image> findByPostIdAndIsNotCompleted(String postId) {
    return jdbcTemplate.query(
        "SELECT * FROM IMAGE AS image WHERE image.POST_ID = ? AND image.STATUS <> 'COMPLETE'",
        new ImageRowMapper(),
        postId);
  }

  @Override
  public int stopByPostIdAndIsNotCompleted(String postId) {
    return jdbcTemplate.update(
        "UPDATE IMAGE AS image SET image.STATUS = 'STOPPED' WHERE image.POST_ID = ? AND image.STATUS <> 'COMPLETE'",
        postId);
  }

  @Override
  public List<Image> findByPostIdAndIsError(String postId) {
    return jdbcTemplate.query(
        "SELECT * FROM IMAGE AS image WHERE image.POST_ID = ? AND image.STATUS = 'ERROR'",
        new ImageRowMapper(),
        postId);
  }

  @Override
  public Optional<Image> findById(Long id) {
    List<Image> images =
        jdbcTemplate.query(
            "SELECT * FROM IMAGE AS image WHERE image.ID = ?", new ImageRowMapper(), id);
    if (images.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(images.get(0));
    }
  }

  @Override
  public int updateStatus(Status status, Long id) {
    int mutationCount =
        jdbcTemplate.update(
            "UPDATE IMAGE AS image SET image.STATUS = ? WHERE image.ID = ?", status.name(), id);
    eventBus.publishEvent(Event.wrap(Event.Kind.IMAGE_UPDATE, id));
    return mutationCount;
  }

  @Override
  public int updateCurrent(long current, Long id) {
    int mutationCount =
        jdbcTemplate.update(
            "UPDATE IMAGE AS image SET image.CURRENT = ? WHERE image.ID = ?", current, id);
    eventBus.publishEvent(Event.wrap(Event.Kind.IMAGE_UPDATE, id));
    return mutationCount;
  }

  @Override
  public int updateTotal(long total, Long id) {
    int mutationCount =
        jdbcTemplate.update(
            "UPDATE IMAGE AS image SET image.TOTAL = ? WHERE image.ID = ?", total, id);
    eventBus.publishEvent(Event.wrap(Event.Kind.IMAGE_UPDATE, id));
    return mutationCount;
  }
}

class ImageRowMapper implements RowMapper<Image> {

  @Override
  public Image mapRow(ResultSet rs, int rowNum) throws SQLException {
    Image image = new Image();
    image.setId(rs.getLong("ID"));
    String host = rs.getString("HOST");
    image.setHost(
        SpringContext.getBeansOfType(Host.class).values().stream()
            .filter(e -> e.getHost().equals(host))
            .findAny()
            .orElse(null));
    image.setUrl(rs.getString("URL"));
    image.setThumbUrl(rs.getString("THUMB_URL"));
    image.setIndex(rs.getInt("INDEX"));
    image.setCurrent(rs.getLong("CURRENT"));
    image.setTotal(rs.getLong("TOTAL"));
    image.setStatus(Status.valueOf(rs.getString("STATUS")));
    image.setPostId(rs.getString("POST_ID"));
    image.setPostIdRef(rs.getLong("POST_ID_REF"));
    return image;
  }
}
