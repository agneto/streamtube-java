package com.streamtube.domain.channel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port for channel persistence. */
public interface ChannelRepository {

  Channel save(Channel channel);

  Optional<Channel> findByUserId(UUID userId);

  Optional<Channel> findByNickname(String nickname);

  boolean existsByNickname(String nickname);

  /** Batch lookup (subscriptions listing): result order is unspecified. */
  List<Channel> findByIds(Collection<UUID> ids);

  /** Batch lookup by owner user ids (comment authors): result order is unspecified. */
  List<Channel> findByUserIds(Collection<UUID> userIds);
}
