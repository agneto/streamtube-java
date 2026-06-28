package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.UserJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepository {

  private final UserJpaRepository jpa;

  public UserRepositoryAdapter(UserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public User save(User user) {
    return PersistenceMapper.toDomain(jpa.save(PersistenceMapper.toEntity(user)));
  }

  @Override
  public Optional<User> findById(UUID id) {
    return jpa.findById(id).map(PersistenceMapper::toDomain);
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return jpa.findByEmail(email).map(PersistenceMapper::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return jpa.existsByEmail(email);
  }
}
