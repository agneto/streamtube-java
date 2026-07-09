package com.streamtube.application.social;

import com.streamtube.application.social.result.SubscriptionView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.social.Subscription;
import com.streamtube.domain.social.SubscriptionRepository;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Channels the caller follows, most recently subscribed first (one batch channel lookup). */
@Service
public class ListMySubscriptionsUseCase {

  private final SubscriptionRepository subscriptions;
  private final ChannelRepository channelRepository;

  public ListMySubscriptionsUseCase(
      SubscriptionRepository subscriptions, ChannelRepository channelRepository) {
    this.subscriptions = subscriptions;
    this.channelRepository = channelRepository;
  }

  @Transactional(readOnly = true)
  public PageResult<SubscriptionView> execute(UUID userId, int page, int size) {
    PageResult<Subscription> result =
        subscriptions.findPageByUserId(
            userId, SocialPageRequests.page(page), SocialPageRequests.size(size));
    Map<UUID, Channel> byId =
        channelRepository
            .findByIds(result.items().stream().map(Subscription::channelId).toList())
            .stream()
            .collect(Collectors.toMap(Channel::id, Function.identity()));
    return result.map(s -> SubscriptionView.from(s, byId.get(s.channelId())));
  }
}
