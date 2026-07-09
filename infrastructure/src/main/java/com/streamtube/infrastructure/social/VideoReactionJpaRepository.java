package com.streamtube.infrastructure.social;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * All mutations are native and guarded by affected-row counts, so the adapter can move the
 * denormalized counters by exactly what actually changed — concurrent requests can never
 * double-count (the PK is the source of truth).
 */
public interface VideoReactionJpaRepository
    extends JpaRepository<VideoReactionEntity, VideoReactionEntity.Key> {

  @Query(
      value = "select type from video_reactions where user_id = :userId and video_id = :videoId",
      nativeQuery = true)
  Optional<String> findType(@Param("userId") UUID userId, @Param("videoId") UUID videoId);

  /** @return 1 when inserted, 0 when the user already reacted (any type). */
  @Modifying
  @Query(
      value =
          "insert into video_reactions (user_id, video_id, type) values (:userId, :videoId, :type)"
              + " on conflict (user_id, video_id) do nothing",
      nativeQuery = true)
  int insertIgnore(
      @Param("userId") UUID userId, @Param("videoId") UUID videoId, @Param("type") String type);

  /** @return 1 when the type actually changed, 0 when it was already {@code type} or absent. */
  @Modifying
  @Query(
      value =
          "update video_reactions set type = :type"
              + " where user_id = :userId and video_id = :videoId and type <> :type",
      nativeQuery = true)
  int switchType(
      @Param("userId") UUID userId, @Param("videoId") UUID videoId, @Param("type") String type);

  /** Type in the WHERE keeps delete + counter decrement consistent under races. */
  @Modifying
  @Query(
      value =
          "delete from video_reactions"
              + " where user_id = :userId and video_id = :videoId and type = :type",
      nativeQuery = true)
  int deleteByType(
      @Param("userId") UUID userId, @Param("videoId") UUID videoId, @Param("type") String type);

  @Modifying
  @Query(
      value =
          "update videos set likes_count = likes_count + :likes,"
              + " dislikes_count = dislikes_count + :dislikes where id = :videoId",
      nativeQuery = true)
  void adjustCounters(
      @Param("videoId") UUID videoId, @Param("likes") long likes, @Param("dislikes") long dislikes);
}
