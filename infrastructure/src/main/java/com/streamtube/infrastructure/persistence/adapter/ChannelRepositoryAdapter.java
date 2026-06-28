package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.ChannelJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelRepositoryAdapter implements ChannelRepository {

  private final ChannelJpaRepository jpa;

  public ChannelRepositoryAdapter(ChannelJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Channel save(Channel channel) {
    return PersistenceMapper.toDomain(jpa.save(PersistenceMapper.toEntity(channel)));
  }

  @Override
  public Optional<Channel> findByUserId(UUID userId) {
    return jpa.findByUserId(userId).map(PersistenceMapper::toDomain);
  }

  @Override
  public boolean existsByNickname(String nickname) {
    return jpa.existsByNickname(nickname);
  }
}
