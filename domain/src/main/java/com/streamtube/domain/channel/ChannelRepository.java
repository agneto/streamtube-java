package com.streamtube.domain.channel;

import java.util.Optional;
import java.util.UUID;

/** Output port for channel persistence. */
public interface ChannelRepository {

  Channel save(Channel channel);

  Optional<Channel> findByUserId(UUID userId);

  Optional<Channel> findByNickname(String nickname);

  boolean existsByNickname(String nickname);
}
