package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.infrastructure.persistence.entity.RefreshTokenEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  List<RefreshTokenEntity> findByFamily(UUID family);

  @Modifying
  @Query(
      "UPDATE RefreshTokenEntity t SET t.revokedAt = :now "
          + "WHERE t.family = :family AND t.revokedAt IS NULL")
  void revokeFamily(@Param("family") UUID family, @Param("now") Instant now);
}
