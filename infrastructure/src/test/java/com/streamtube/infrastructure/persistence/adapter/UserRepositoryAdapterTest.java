package com.streamtube.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.streamtube.domain.shared.AuthExceptions.EmailAlreadyRegisteredException;
import com.streamtube.domain.user.User;
import com.streamtube.infrastructure.persistence.entity.UserEntity;
import com.streamtube.infrastructure.persistence.repository.UserJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

class UserRepositoryAdapterTest {

  /** A registration racing past the exists-check must surface as the domain 409, never a 500. */
  @Test
  void translatesUniqueViolationToEmailAlreadyRegistered() {
    UserJpaRepository jpa = Mockito.mock(UserJpaRepository.class);
    when(jpa.saveAndFlush(any(UserEntity.class)))
        .thenThrow(new DataIntegrityViolationException("users_email_key"));
    UserRepositoryAdapter adapter = new UserRepositoryAdapter(jpa);

    User user = User.register(UUID.randomUUID(), "dup@test.com", "hash", Instant.now());

    assertThatThrownBy(() -> adapter.save(user))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }
}
