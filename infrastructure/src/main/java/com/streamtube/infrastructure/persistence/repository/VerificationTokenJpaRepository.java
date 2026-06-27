package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.domain.auth.VerificationTokenType;
import com.streamtube.infrastructure.persistence.entity.VerificationTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenJpaRepository
    extends JpaRepository<VerificationTokenEntity, UUID> {

  Optional<VerificationTokenEntity> findByTokenHashAndType(
      String tokenHash, VerificationTokenType type);
}
