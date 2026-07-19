package com.streamtube.application.social;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.notification.Notification;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.SocialExceptions.SelfSubscriptionException;
import com.streamtube.domain.social.SubscriptionRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Subscribes the caller to a channel. Idempotent; subscribing to your own channel is a 400. */
@Service
public class SubscribeUseCase {

  private final ChannelRepository channelRepository;
  private final SubscriptionRepository subscriptions;
  private final NotificationRepository notifications;
  private final Clock clock;

  public SubscribeUseCase(
      ChannelRepository channelRepository,
      SubscriptionRepository subscriptions,
      NotificationRepository notifications,
      Clock clock) {
    this.channelRepository = channelRepository;
    this.subscriptions = subscriptions;
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional
  public void execute(UUID userId, String nickname) {
    Channel channel =
        channelRepository.findByNickname(nickname).orElseThrow(ChannelNotFoundException::new);
    if (channel.userId().equals(userId)) {
      throw new SelfSubscriptionException();
    }
    // Notify the owner only when a row is actually inserted — re-subscribing must not spam,
    // mirroring the subscribers-count rule. Written in the same transaction (ADV-01).
    if (subscriptions.subscribe(userId, channel.id())) {
      Channel subscriberChannel =
          channelRepository.findByUserId(userId).orElseThrow(ChannelNotFoundException::new);
      notifications.create(
          Notification.newSubscriber(
              UUID.randomUUID(), channel.userId(), subscriberChannel.id(), clock.instant()));
    }
  }
}
