package me.vripper.jpa.repositories;

import java.util.List;
import java.util.Optional;
import me.vripper.jpa.domain.LogEvent;

public interface ILogEventRepository extends IRepository {

  LogEvent save(LogEvent logEvent);

  LogEvent update(LogEvent logEvent);

  List<LogEvent> findAll();

  Optional<LogEvent> findById(Long id);

  void delete(Long id);

  void deleteAll();
}
