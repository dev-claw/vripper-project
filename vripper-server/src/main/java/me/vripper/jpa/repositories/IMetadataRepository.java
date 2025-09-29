package me.vripper.jpa.repositories;

import java.util.Optional;
import me.vripper.jpa.domain.Metadata;

public interface IMetadataRepository extends IRepository {

  Metadata save(Metadata metadata);

  Optional<Metadata> findByPostId(String postId);

  int deleteByPostId(String postId);
}
