package com.streamtube.infrastructure.persistence.mapper;

import com.streamtube.domain.auth.RefreshToken;
import com.streamtube.domain.auth.VerificationToken;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.user.User;
import com.streamtube.infrastructure.persistence.entity.ChannelEntity;
import com.streamtube.infrastructure.persistence.entity.RefreshTokenEntity;
import com.streamtube.infrastructure.persistence.entity.UserEntity;
import com.streamtube.infrastructure.persistence.entity.VerificationTokenEntity;

/** Hand-written mappers between pure domain entities and JPA persistence entities. */
public final class PersistenceMapper {

  private PersistenceMapper() {}

  public static UserEntity toEntity(User u) {
    return new UserEntity(
        u.id(), u.email(), u.passwordHash(), u.isConfirmed(), u.createdAt(), u.updatedAt());
  }

  public static User toDomain(UserEntity e) {
    return new User(
        e.getId(),
        e.getEmail(),
        e.getPassword(),
        e.isConfirmed(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  public static ChannelEntity toEntity(Channel c) {
    return new ChannelEntity(
        c.id(), c.userId(), c.name(), c.nickname(), c.description(), c.createdAt(), c.updatedAt());
  }

  public static Channel toDomain(ChannelEntity e) {
    return new Channel(
        e.getId(),
        e.getUserId(),
        e.getName(),
        e.getNickname(),
        e.getDescription(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  public static RefreshTokenEntity toEntity(RefreshToken t) {
    return new RefreshTokenEntity(
        t.id(),
        t.userId(),
        t.family(),
        t.jti(),
        t.tokenHash(),
        t.expiresAt(),
        t.revokedAt(),
        t.createdAt());
  }

  public static RefreshToken toDomain(RefreshTokenEntity e) {
    return new RefreshToken(
        e.getId(),
        e.getUserId(),
        e.getFamily(),
        e.getJti(),
        e.getTokenHash(),
        e.getExpiresAt(),
        e.getRevokedAt(),
        e.getCreatedAt());
  }

  public static VerificationTokenEntity toEntity(VerificationToken t) {
    return new VerificationTokenEntity(
        t.id(),
        t.userId(),
        t.type(),
        t.tokenHash(),
        t.expiresAt(),
        t.consumedAt(),
        t.createdAt());
  }

  public static VerificationToken toDomain(VerificationTokenEntity e) {
    return new VerificationToken(
        e.getId(),
        e.getUserId(),
        e.getType(),
        e.getTokenHash(),
        e.getExpiresAt(),
        e.getConsumedAt(),
        e.getCreatedAt());
  }
}
