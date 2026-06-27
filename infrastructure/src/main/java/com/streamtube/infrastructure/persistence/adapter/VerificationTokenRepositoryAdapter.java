package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.auth.VerificationToken;
import com.streamtube.domain.auth.VerificationTokenRepository;
import com.streamtube.domain.auth.VerificationTokenType;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.VerificationTokenJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class VerificationTokenRepositoryAdapter implements VerificationTokenRepository {

  private final VerificationTokenJpaRepository jpa;

  public VerificationTokenRepositoryAdapter(VerificationTokenJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public VerificationToken save(VerificationToken token) {
    return PersistenceMapper.toDomain(jpa.save(PersistenceMapper.toEntity(token)));
  }

  @Override
  public Optional<VerificationToken> findByTokenHashAndType(
      String tokenHash, VerificationTokenType type) {
    return jpa.findByTokenHashAndType(tokenHash, type).map(PersistenceMapper::toDomain);
  }
}
