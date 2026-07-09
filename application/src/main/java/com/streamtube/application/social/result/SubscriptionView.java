package com.streamtube.application.social.result;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.social.Subscription;
import java.time.Instant;
import java.util.UUID;

/** Listing item for the subscribed-channels area. */
public record SubscriptionView(
    UUID channelId,
    String name,
    String nickname,
    String description,
    long subscribersCount,
    Instant subscribedAt) {

  public static SubscriptionView from(Subscription subscription, Channel channel) {
    return new SubscriptionView(
        channel.id(),
        channel.name(),
        channel.nickname(),
        channel.description(),
        channel.subscribersCount(),
        subscription.createdAt());
  }
}
