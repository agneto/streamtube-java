package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.shared.AuthExceptions.EmailAlreadyRegisteredException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.UserJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepository {

  private final UserJpaRepository jpa;

  public UserRepositoryAdapter(UserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public User save(User user) {
    try {
      // Flush now so a unique-violation surfaces here (not at commit) and can be translated:
      // two registrations racing past the exists-check must yield 409, never 500. The only
      // unique constraint on users is the email.
      return PersistenceMapper.toDomain(jpa.saveAndFlush(PersistenceMapper.toEntity(user)));
    } catch (DataIntegrityViolationException e) {
      throw new EmailAlreadyRegisteredException();
    }
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
