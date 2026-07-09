package com.streamtube.application.social;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.SocialExceptions.SelfSubscriptionException;
import com.streamtube.domain.social.SubscriptionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Subscribes the caller to a channel. Idempotent; subscribing to your own channel is a 400. */
@Service
public class SubscribeUseCase {

  private final ChannelRepository channelRepository;
  private final SubscriptionRepository subscriptions;

  public SubscribeUseCase(
      ChannelRepository channelRepository, SubscriptionRepository subscriptions) {
    this.channelRepository = channelRepository;
    this.subscriptions = subscriptions;
  }

  @Transactional
  public void execute(UUID userId, String nickname) {
    Channel channel =
        channelRepository.findByNickname(nickname).orElseThrow(ChannelNotFoundException::new);
    if (channel.userId().equals(userId)) {
      throw new SelfSubscriptionException();
    }
    subscriptions.subscribe(userId, channel.id());
  }
}
