package me.vripper.jpa.repositories.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import me.vripper.jpa.domain.Metadata;
import me.vripper.jpa.domain.Post;
import me.vripper.jpa.domain.enums.Status;
import me.vripper.jpa.repositories.IPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class PostRepository implements IPostRepository {

  private final JdbcTemplate jdbcTemplate;
  private final EventBus eventBus;

  @Autowired
  public PostRepository(JdbcTemplate jdbcTemplate, EventBus eventBus) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventBus = eventBus;
  }

  private synchronized Long nextId() {
    return jdbcTemplate.queryForObject("CALL NEXT VALUE FOR SEQ_POST", Long.class);
  }

  @Override
  public Post save(Post post) {
    long id = nextId();
    jdbcTemplate.update(
        "INSERT INTO POST (ID, DONE, FORUM, HOSTS, POST_FOLDER_NAME, POST_ID, PREVIEWS, SECURITY_TOKEN, STATUS, THANKED, THREAD_ID, THREAD_TITLE, TITLE, TOTAL, URL, ADDED_ON, RANK) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        post.getDone(),
        post.getForum(),
        String.join(";", post.getHosts()),
        post.getDownloadDirectory(),
        post.getPostId(),
        String.join(";", post.getPreviews()),
        post.getSecurityToken(),
        post.getStatus().name(),
        post.isThanked(),
        post.getThreadId(),
        post.getThreadTitle(),
        post.getTitle(),
        post.getTotal(),
        post.getUrl(),
        Timestamp.valueOf(post.getAddedOn()),
        post.getRank());
    post.setId(id);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_UPDATE, id));
    return post;
  }

  @Override
  public Optional<Post> findByPostId(String postId) {
    List<Post> posts =
        jdbcTemplate.query(
            "SELECT metadata.*,post.* FROM METADATA metadata FULL JOIN POST post ON metadata.POST_ID_REF = post.ID WHERE post.POST_ID = ?",
            new PostRowMapper(),
            postId);
    if (posts.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(posts.get(0));
    }
  }

  @Override
  public List<String> findCompleted() {
    return jdbcTemplate.query(
        "SELECT POST_ID FROM POST AS post WHERE status = 'COMPLETE' AND done >= total",
        ((rs, rowNum) -> rs.getString("POST_ID")));
  }

  @Override
  public Optional<Post> findById(Long id) {
    List<Post> posts =
        jdbcTemplate.query(
            "SELECT metadata.*,post.* FROM METADATA metadata FULL JOIN POST post ON metadata.POST_ID_REF = post.ID WHERE post.ID = ?",
            new PostRowMapper(),
            id);
    if (posts.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(posts.get(0));
    }
  }

  @Override
  public List<Post> findAll() {
    return jdbcTemplate.query(
        "SELECT metadata.*,post.* FROM METADATA metadata FULL JOIN POST post ON metadata.POST_ID_REF = post.ID",
        new PostRowMapper());
  }

  @Override
  public boolean existByPostId(String postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM POST AS post WHERE post.POST_ID = ?", Integer.class, postId);
    if (count == null) {
      return false;
    } else {
      return count > 0;
    }
  }

  @Override
  public int setDownloadingToStopped() {
    return jdbcTemplate.update(
        "UPDATE POST AS post SET post.STATUS = 'STOPPED' WHERE post.STATUS = 'DOWNLOADING' OR post.STATUS = 'PARTIAL' OR post.STATUS = 'PENDING'");
  }

  @Override
  public int deleteByPostId(String postId) {
    int mutationCount =
        jdbcTemplate.update("DELETE FROM POST AS post WHERE post.POST_ID = ?", postId);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_REMOVE, postId));
    return mutationCount;
  }

  @Override
  public int updateStatus(Status status, Long id) {
    int mutationCount =
        jdbcTemplate.update(
            "UPDATE POST AS post SET post.STATUS = ? WHERE post.ID = ?", status.name(), id);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_UPDATE, id));
    return mutationCount;
  }

  @Override
  public int updateDone(int done, Long id) {
    int mutationCount =
        jdbcTemplate.update("UPDATE POST AS post SET post.DONE = ? WHERE post.ID = ?", done, id);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_UPDATE, id));
    return mutationCount;
  }

  @Override
  public int updateFolderName(String postFolderName, Long id) {
    int mutationCount =
        jdbcTemplate.update(
            "UPDATE POST AS post SET post.POST_FOLDER_NAME = ? WHERE post.ID = ?",
            postFolderName,
            id);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_UPDATE, id));
    return mutationCount;
  }

  @Override
  public int updateTitle(String title, Long id) {
    int mutationCount =
        jdbcTemplate.update("UPDATE POST AS post SET post.TITLE = ? WHERE post.ID = ?", title, id);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_UPDATE, id));
    return mutationCount;
  }

  @Override
  public int updateThanked(boolean thanked, Long id) {
    int mutationCount =
        jdbcTemplate.update(
            "UPDATE POST AS post SET post.THANKED = ? WHERE post.ID = ?", thanked, id);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_UPDATE, id));
    return mutationCount;
  }

  @Override
  public int updateRank(int rank, Long id) {
    int mutationCount =
        jdbcTemplate.update("UPDATE POST AS post SET post.RANK = ? WHERE post.ID = ?", rank, id);
    eventBus.publishEvent(Event.wrap(Event.Kind.POST_UPDATE, id));
    return mutationCount;
  }
}

class PostRowMapper implements RowMapper<Post> {

  private static final String DELIMITER = ";";

  @Override
  public Post mapRow(ResultSet rs, int rowNum) throws SQLException {

    Post post = new Post();
    post.setId(rs.getLong("post.ID"));
    post.setStatus(Status.valueOf(rs.getString("post.STATUS")));
    post.setPostId(rs.getString("post.POST_ID"));
    post.setThreadTitle(rs.getString("post.THREAD_TITLE"));
    post.setThreadId(rs.getString("post.THREAD_ID"));
    post.setTitle(rs.getString("post.TITLE"));
    post.setUrl(rs.getString("post.URL"));
    post.setDone(rs.getInt("post.DONE"));
    post.setTotal(rs.getInt("post.TOTAL"));
    post.setHosts(Set.of(rs.getString("post.HOSTS").split(DELIMITER)));
    post.setForum(rs.getString("post.FORUM"));
    post.setSecurityToken(rs.getString("post.SECURITY_TOKEN"));
    post.setDownloadDirectory(rs.getString("post.POST_FOLDER_NAME"));
    post.setThanked(rs.getBoolean("post.THANKED"));
    String previews;
    if ((previews = rs.getString("post.PREVIEWS")) != null) {
      post.setPreviews(Set.of(previews.split(DELIMITER)));
    }
    post.setAddedOn(rs.getTimestamp("post.ADDED_ON").toLocalDateTime());
    post.setRank(rs.getInt("post.RANK"));

    Long metadataId = rs.getLong("metadata.POST_ID_REF");
    if (!rs.wasNull()) {
      Metadata metadata = new Metadata();
      metadata.setPostIdRef(metadataId);
      metadata.setPostId(rs.getString("metadata.POST_ID"));
      String resolvedNames = rs.getString("metadata.RESOLVED_NAMES");
      if (resolvedNames != null && !resolvedNames.isBlank()) {
        metadata.setResolvedNames(List.of(resolvedNames.split("%sep%")));
      }
      metadata.setPostedBy(rs.getString("metadata.POSTED_BY"));
      post.setMetadata(metadata);
    }

    return post;
  }
}
