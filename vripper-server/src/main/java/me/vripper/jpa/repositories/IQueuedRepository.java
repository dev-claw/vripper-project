package me.vripper.jpa.repositories;

import java.util.List;
import java.util.Optional;
import me.vripper.jpa.domain.Queued;

public interface IQueuedRepository extends IRepository {
  Queued save(Queued queued);

  Optional<Queued> findByThreadId(String threadId);

  List<Queued> findAll();

  Optional<Queued> findById(Long id);

  int deleteByThreadId(String threadId);

  void deleteAll();
}
