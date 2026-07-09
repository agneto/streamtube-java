package com.streamtube.application.social;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.social.SubscriptionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Unsubscribes the caller from a channel. Idempotent (no error when not subscribed). */
@Service
public class UnsubscribeUseCase {

  private final ChannelRepository channelRepository;
  private final SubscriptionRepository subscriptions;

  public UnsubscribeUseCase(
      ChannelRepository channelRepository, SubscriptionRepository subscriptions) {
    this.channelRepository = channelRepository;
    this.subscriptions = subscriptions;
  }

  @Transactional
  public void execute(UUID userId, String nickname) {
    Channel channel =
        channelRepository.findByNickname(nickname).orElseThrow(ChannelNotFoundException::new);
    subscriptions.unsubscribe(userId, channel.id());
  }
}
