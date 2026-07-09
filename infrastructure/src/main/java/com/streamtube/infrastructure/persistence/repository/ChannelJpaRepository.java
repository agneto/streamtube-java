package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.infrastructure.persistence.entity.ChannelEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelJpaRepository extends JpaRepository<ChannelEntity, UUID> {

  Optional<ChannelEntity> findByUserId(UUID userId);

  Optional<ChannelEntity> findByNickname(String nickname);

  boolean existsByNickname(String nickname);

  List<ChannelEntity> findByUserIdIn(Collection<UUID> userIds);
}
