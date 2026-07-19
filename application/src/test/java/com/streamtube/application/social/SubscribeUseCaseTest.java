package com.streamtube.application.social;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.notification.Notification;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.notification.NotificationType;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.SocialExceptions.SelfSubscriptionException;
import com.streamtube.domain.social.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SubscribeUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private ChannelRepository channels;
  private SubscriptionRepository subscriptions;
  private NotificationRepository notifications;
  private SubscribeUseCase useCase;
  private UUID channelOwnerId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    channels = Mockito.mock(ChannelRepository.class);
    subscriptions = Mockito.mock(SubscriptionRepository.class);
    notifications = Mockito.mock(NotificationRepository.class);
    useCase =
        new SubscribeUseCase(
            channels, subscriptions, notifications, Clock.fixed(NOW, ZoneOffset.UTC));

    channelOwnerId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByNickname("canal"))
        .thenReturn(
            Optional.of(Channel.createForUser(channelId, channelOwnerId, "Canal", "canal", NOW)));
  }

  @Test
  void subscribesToAnotherUsersChannel() {
    UUID followerId = UUID.randomUUID();
    when(subscriptions.subscribe(followerId, channelId)).thenReturn(true);
    UUID followerChannelId = UUID.randomUUID();
    when(channels.findByUserId(followerId))
        .thenReturn(
            Optional.of(Channel.createForUser(followerChannelId, followerId, "Eu", "eu", NOW)));

    useCase.execute(followerId, "canal");

    verify(subscriptions).subscribe(followerId, channelId);
  }

  @Test
  void realSubscribeNotifiesTheOwner() {
    UUID followerId = UUID.randomUUID();
    UUID followerChannelId = UUID.randomUUID();
    when(subscriptions.subscribe(followerId, channelId)).thenReturn(true);
    when(channels.findByUserId(followerId))
        .thenReturn(
            Optional.of(Channel.createForUser(followerChannelId, followerId, "Eu", "eu", NOW)));

    useCase.execute(followerId, "canal");

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).create(captor.capture());
    Notification n = captor.getValue();
    Assertions.assertThat(n.type()).isEqualTo(NotificationType.NEW_SUBSCRIBER);
    Assertions.assertThat(n.recipientUserId()).isEqualTo(channelOwnerId);
    Assertions.assertThat(n.actorChannelId()).isEqualTo(followerChannelId);
  }

  @Test
  void reSubscribeDoesNotNotify() {
    UUID followerId = UUID.randomUUID();
    when(subscriptions.subscribe(followerId, channelId)).thenReturn(false);

    useCase.execute(followerId, "canal");

    verify(notifications, never()).create(any());
  }

  @Test
  void selfSubscriptionIsRejected() {
    assertThatThrownBy(() -> useCase.execute(channelOwnerId, "canal"))
        .isInstanceOf(SelfSubscriptionException.class);
    verify(subscriptions, never()).subscribe(any(), any());
    verify(notifications, never()).create(any());
  }

  @Test
  void unknownChannelIs404() {
    when(channels.findByNickname("nao-existe")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), "nao-existe"))
        .isInstanceOf(ChannelNotFoundException.class);
  }
}
