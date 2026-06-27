package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.infrastructure.persistence.entity.ChannelEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelJpaRepository extends JpaRepository<ChannelEntity, UUID> {

  Optional<ChannelEntity> findByUserId(UUID userId);

  boolean existsByNickname(String nickname);
}
