package com.streamtube.infrastructure.social;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Same contract as {@link VideoReactionJpaRepository}, over comment counters. */
public interface CommentReactionJpaRepository
    extends JpaRepository<CommentReactionEntity, CommentReactionEntity.Key> {

  @Query(
      value =
          "select type from comment_reactions where user_id = :userId and comment_id = :commentId",
      nativeQuery = true)
  Optional<String> findType(@Param("userId") UUID userId, @Param("commentId") UUID commentId);

  @Modifying
  @Query(
      value =
          "insert into comment_reactions (user_id, comment_id, type)"
              + " values (:userId, :commentId, :type)"
              + " on conflict (user_id, comment_id) do nothing",
      nativeQuery = true)
  int insertIgnore(
      @Param("userId") UUID userId,
      @Param("commentId") UUID commentId,
      @Param("type") String type);

  @Modifying
  @Query(
      value =
          "update comment_reactions set type = :type"
              + " where user_id = :userId and comment_id = :commentId and type <> :type",
      nativeQuery = true)
  int switchType(
      @Param("userId") UUID userId,
      @Param("commentId") UUID commentId,
      @Param("type") String type);

  @Modifying
  @Query(
      value =
          "delete from comment_reactions"
              + " where user_id = :userId and comment_id = :commentId and type = :type",
      nativeQuery = true)
  int deleteByType(
      @Param("userId") UUID userId,
      @Param("commentId") UUID commentId,
      @Param("type") String type);

  @Modifying
  @Query(
      value =
          "update comments set likes_count = likes_count + :likes,"
              + " dislikes_count = dislikes_count + :dislikes where id = :commentId",
      nativeQuery = true)
  void adjustCounters(
      @Param("commentId") UUID commentId,
      @Param("likes") long likes,
      @Param("dislikes") long dislikes);
}
