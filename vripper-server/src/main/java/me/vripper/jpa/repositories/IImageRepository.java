package me.vripper.jpa.repositories;

import java.util.List;
import java.util.Optional;
import me.vripper.jpa.domain.Image;
import me.vripper.jpa.domain.enums.Status;

public interface IImageRepository extends IRepository {

  Image save(Image image);

  void deleteAllByPostId(String postId);

  List<Image> findByPostId(String postId);

  Integer countError();

  List<Image> findByPostIdAndIsNotCompleted(String postId);

  int stopByPostIdAndIsNotCompleted(String postId);

  List<Image> findByPostIdAndIsError(String postId);

  Optional<Image> findById(Long id);

  int updateStatus(Status status, Long id);

  int updateCurrent(long current, Long id);

  int updateTotal(long total, Long id);
}
