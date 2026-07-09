package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.domain.video.Visibility;
import com.streamtube.infrastructure.persistence.entity.VideoEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoJpaRepository extends JpaRepository<VideoEntity, UUID> {

  Optional<VideoEntity> findBySlug(String slug);

  boolean existsBySlug(String slug);

  Page<VideoEntity> findByChannelIdOrderByCreatedAtDesc(UUID channelId, Pageable pageable);

  Page<VideoEntity> findByChannelIdAndVisibilityAndPublishedAtNotNullOrderByPublishedAtDesc(
      UUID channelId, Visibility visibility, Pageable pageable);

  // Native so the increment stays a single atomic statement and bypasses the entity mapping
  // (views_count is updatable = false there on purpose).
  @Modifying
  @Query(value = "update videos set views_count = views_count + 1 where id = :id",
      nativeQuery = true)
  void incrementViews(@Param("id") UUID id);

  List<VideoEntity>
      findByCategoryIdAndIdNotAndVisibilityAndPublishedAtNotNullOrderByPublishedAtDesc(
          UUID categoryId, UUID excludeId, Visibility visibility, Pageable pageable);

  List<VideoEntity> findByIdNotAndVisibilityAndPublishedAtNotNullOrderByPublishedAtDesc(
      UUID excludeId, Visibility visibility, Pageable pageable);

  // Native on purpose: JPQL against SubscriptionEntity would break the worker's bootstrap (its
  // persistence unit does not map the social entities); native SQL is not validated at startup.
  @Query(
      value =
          "select v.* from videos v join subscriptions s on s.channel_id = v.channel_id"
              + " where s.user_id = :userId and v.visibility = 'PUBLIC'"
              + " and v.published_at is not null order by v.published_at desc",
      countQuery =
          "select count(*) from videos v join subscriptions s on s.channel_id = v.channel_id"
              + " where s.user_id = :userId and v.visibility = 'PUBLIC'"
              + " and v.published_at is not null",
      nativeQuery = true)
  Page<VideoEntity> findSubscriptionFeed(@Param("userId") UUID userId, Pageable pageable);
}
