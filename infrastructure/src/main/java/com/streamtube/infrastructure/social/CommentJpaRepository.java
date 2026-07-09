package com.streamtube.infrastructure.social;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentJpaRepository extends JpaRepository<CommentEntity, UUID> {

  Page<CommentEntity> findByVideoIdAndParentIdIsNullOrderByCreatedAtDesc(
      UUID videoId, Pageable pageable);

  Page<CommentEntity> findByParentIdOrderByCreatedAtAsc(UUID parentId, Pageable pageable);

  /** Bulk delete returning the affected count — exact input for the counter decrement. */
  @Modifying
  @Query("delete from CommentEntity c where c.parentId = :parentId")
  int deleteReplies(@Param("parentId") UUID parentId);

  @Modifying
  @Query(
      value = "update videos set comments_count = comments_count + :delta where id = :videoId",
      nativeQuery = true)
  void adjustVideoCommentsCount(@Param("videoId") UUID videoId, @Param("delta") long delta);

  @Modifying
  @Query(
      value = "update comments set replies_count = replies_count + :delta where id = :parentId",
      nativeQuery = true)
  void adjustRepliesCount(@Param("parentId") UUID parentId, @Param("delta") long delta);
}
