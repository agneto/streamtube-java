package com.streamtube.infrastructure.social;

import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.social.Subscription;
import com.streamtube.domain.social.SubscriptionRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Idempotent subscribe/unsubscribe: the counter only moves when a row actually changed. */
@Repository
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

  private final SubscriptionJpaRepository jpa;

  public SubscriptionRepositoryAdapter(SubscriptionJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public boolean subscribe(UUID userId, UUID channelId) {
    boolean created = jpa.insertIgnore(userId, channelId) == 1;
    if (created) {
      jpa.adjustSubscribersCount(channelId, 1);
    }
    return created;
  }

  @Override
  public boolean unsubscribe(UUID userId, UUID channelId) {
    boolean removed = jpa.deleteSubscription(userId, channelId) == 1;
    if (removed) {
      jpa.adjustSubscribersCount(channelId, -1);
    }
    return removed;
  }

  @Override
  public boolean exists(UUID userId, UUID channelId) {
    return jpa.existsByUserIdAndChannelId(userId, channelId);
  }

  @Override
  public PageResult<Subscription> findPageByUserId(UUID userId, int page, int size) {
    Page<SubscriptionEntity> result =
        jpa.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    return new PageResult<>(
        result.getContent().stream()
            .map(e -> new Subscription(e.getUserId(), e.getChannelId(), e.getCreatedAt()))
            .toList(),
        page,
        size,
        result.getTotalElements());
  }
}
