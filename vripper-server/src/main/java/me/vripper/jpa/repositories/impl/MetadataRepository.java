package me.vripper.jpa.repositories.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import me.vripper.jpa.domain.Metadata;
import me.vripper.jpa.repositories.IMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class MetadataRepository implements IMetadataRepository {

  private final JdbcTemplate jdbcTemplate;
  private final EventBus eventBus;

  @Autowired
  public MetadataRepository(JdbcTemplate jdbcTemplate, EventBus eventBus) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventBus = eventBus;
  }

  @Override
  public Metadata save(Metadata metadata) {
    jdbcTemplate.update(
        "INSERT INTO METADATA (POST_ID_REF, POST_ID, POSTED_BY, RESOLVED_NAMES) VALUES (?,?,?,?)",
        metadata.getPostIdRef(),
        metadata.getPostId(),
        metadata.getPostedBy(),
        String.join("%sep%", metadata.getResolvedNames()));
    eventBus.publishEvent(Event.wrap(Event.Kind.METADATA_UPDATE, metadata.getPostIdRef()));
    return metadata;
  }

  @Override
  public Optional<Metadata> findByPostId(String postId) {
    List<Metadata> metadata =
        jdbcTemplate.query(
            "SELECT metadata.* FROM METADATA AS metadata WHERE metadata.POST_ID = ?",
            new MetadataRowMapper(),
            postId);
    if (metadata.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(metadata.get(0));
    }
  }

  @Override
  public int deleteByPostId(String postId) {
    return jdbcTemplate.update(
        "DELETE FROM METADATA AS metadata WHERE metadata.POST_ID = ?", postId);
  }
}

class MetadataRowMapper implements RowMapper<Metadata> {

  @Override
  public Metadata mapRow(ResultSet rs, int rowNum) throws SQLException {
    Metadata metadata = new Metadata();
    metadata.setPostIdRef(rs.getLong("POST_ID_REF"));
    metadata.setPostId(rs.getString("POST_ID"));
    metadata.setPostedBy(rs.getString("POSTED_BY"));
    String resolvedNames = rs.getString("RESOLVED_NAMES");
    if (resolvedNames != null && !resolvedNames.isBlank()) {
      metadata.setResolvedNames(List.of(resolvedNames.split("%sep%")));
    }
    return metadata;
  }
}
