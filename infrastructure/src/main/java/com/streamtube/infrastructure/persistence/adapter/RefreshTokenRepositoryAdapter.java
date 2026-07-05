package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.auth.RefreshToken;
import com.streamtube.domain.auth.RefreshTokenRepository;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

  private final RefreshTokenJpaRepository jpa;

  public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public RefreshToken save(RefreshToken token) {
    return PersistenceMapper.toDomain(jpa.save(PersistenceMapper.toEntity(token)));
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    return jpa.findByTokenHash(tokenHash).map(PersistenceMapper::toDomain);
  }

  @Override
  public List<RefreshToken> findByFamily(UUID family) {
    return jpa.findByFamily(family).stream().map(PersistenceMapper::toDomain).toList();
  }

  @Override
  public void revokeFamily(UUID family, Instant now) {
    jpa.revokeFamily(family, now);
  }

  @Override
  public void deleteAllForUser(UUID userId) {
    jpa.deleteAllForUser(userId);
  }
}
