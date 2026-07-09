package com.streamtube.domain.social;

import com.streamtube.domain.shared.PageResult;
import java.util.UUID;

/**
 * Output port for channel subscriptions. Subscribe/unsubscribe are idempotent: the channel's
 * subscribers_count only moves when a row is actually inserted or deleted.
 */
public interface SubscriptionRepository {

  /** @return true when the subscription was created (false: already subscribed). */
  boolean subscribe(UUID userId, UUID channelId);

  /** @return true when the subscription was removed (false: was not subscribed). */
  boolean unsubscribe(UUID userId, UUID channelId);

  boolean exists(UUID userId, UUID channelId);

  /** Channels the user follows, most recently subscribed first. */
  PageResult<Subscription> findPageByUserId(UUID userId, int page, int size);
}
