package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.NicknameAlreadyTakenException;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.ChannelJpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelRepositoryAdapter implements ChannelRepository {

  private final ChannelJpaRepository jpa;

  public ChannelRepositoryAdapter(ChannelJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Channel save(Channel channel) {
    try {
      // Flush now so a unique-violation surfaces here (not at commit) and can be translated:
      // two nickname changes racing past the exists-check must yield 409, never 500. The only
      // unique constraint on channels is the nickname.
      return PersistenceMapper.toDomain(jpa.saveAndFlush(PersistenceMapper.toEntity(channel)));
    } catch (DataIntegrityViolationException e) {
      throw new NicknameAlreadyTakenException();
    }
  }

  @Override
  public Optional<Channel> findByUserId(UUID userId) {
    return jpa.findByUserId(userId).map(PersistenceMapper::toDomain);
  }

  @Override
  public Optional<Channel> findByNickname(String nickname) {
    return jpa.findByNickname(nickname).map(PersistenceMapper::toDomain);
  }

  @Override
  public boolean existsByNickname(String nickname) {
    return jpa.existsByNickname(nickname);
  }

  @Override
  public List<Channel> findByIds(Collection<UUID> ids) {
    return jpa.findAllById(ids).stream().map(PersistenceMapper::toDomain).toList();
  }

  @Override
  public List<Channel> findByUserIds(Collection<UUID> userIds) {
    return jpa.findByUserIdIn(userIds).stream().map(PersistenceMapper::toDomain).toList();
  }
}
