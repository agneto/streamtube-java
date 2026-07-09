package com.streamtube.application.channel;

import com.streamtube.application.channel.result.PublicChannelView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.social.SubscriptionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public channel page header, looked up by nickname, with the subscriber count and whether the
 * viewer follows it ({@code subscribed} is always false for anonymous viewers).
 */
@Service
public class GetPublicChannelUseCase {

  private final ChannelRepository channelRepository;
  private final SubscriptionRepository subscriptions;

  public GetPublicChannelUseCase(
      ChannelRepository channelRepository, SubscriptionRepository subscriptions) {
    this.channelRepository = channelRepository;
    this.subscriptions = subscriptions;
  }

  @Transactional(readOnly = true)
  public PublicChannelView execute(String nickname, UUID viewerUserId) {
    Channel channel =
        channelRepository.findByNickname(nickname).orElseThrow(ChannelNotFoundException::new);
    boolean subscribed = viewerUserId != null && subscriptions.exists(viewerUserId, channel.id());
    return new PublicChannelView(
        channel.id(),
        channel.name(),
        channel.nickname(),
        channel.description(),
        channel.subscribersCount(),
        subscribed,
        channel.createdAt());
  }
}
