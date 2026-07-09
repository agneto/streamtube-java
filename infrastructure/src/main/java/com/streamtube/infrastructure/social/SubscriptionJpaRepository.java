package com.streamtube.infrastructure.social;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionJpaRepository
    extends JpaRepository<SubscriptionEntity, SubscriptionEntity.Key> {

  boolean existsByUserIdAndChannelId(UUID userId, UUID channelId);

  Page<SubscriptionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /** @return 1 when inserted, 0 when already subscribed — gates the counter increment. */
  @Modifying
  @Query(
      value =
          "insert into subscriptions (user_id, channel_id) values (:userId, :channelId)"
              + " on conflict (user_id, channel_id) do nothing",
      nativeQuery = true)
  int insertIgnore(@Param("userId") UUID userId, @Param("channelId") UUID channelId);

  /** @return 1 when removed, 0 when there was no subscription — gates the counter decrement. */
  @Modifying
  @Query(
      value = "delete from subscriptions where user_id = :userId and channel_id = :channelId",
      nativeQuery = true)
  int deleteSubscription(@Param("userId") UUID userId, @Param("channelId") UUID channelId);

  @Modifying
  @Query(
      value =
          "update channels set subscribers_count = subscribers_count + :delta"
              + " where id = :channelId",
      nativeQuery = true)
  void adjustSubscribersCount(@Param("channelId") UUID channelId, @Param("delta") long delta);
}
