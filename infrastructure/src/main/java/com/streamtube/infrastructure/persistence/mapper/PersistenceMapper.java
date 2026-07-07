package com.streamtube.infrastructure.persistence.mapper;

import com.streamtube.domain.auth.RefreshToken;
import com.streamtube.domain.auth.VerificationToken;
import com.streamtube.domain.category.Category;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.user.User;
import com.streamtube.domain.video.Video;
import com.streamtube.infrastructure.persistence.entity.CategoryEntity;
import com.streamtube.infrastructure.persistence.entity.ChannelEntity;
import com.streamtube.infrastructure.persistence.entity.RefreshTokenEntity;
import com.streamtube.infrastructure.persistence.entity.UserEntity;
import com.streamtube.infrastructure.persistence.entity.VerificationTokenEntity;
import com.streamtube.infrastructure.persistence.entity.VideoEntity;

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

  public static VideoEntity toEntity(Video v) {
    return new VideoEntity(
        v.id(),
        v.channelId(),
        v.title(),
        v.slug(),
        v.status(),
        v.storageKey(),
        v.thumbnailKey(),
        v.durationSeconds(),
        v.metadata(),
        v.errorMessage(),
        v.description(),
        v.categoryId(),
        v.visibility(),
        v.publishedAt(),
        v.createdAt(),
        v.updatedAt());
  }

  public static Video toDomain(VideoEntity e) {
    return new Video(
        e.getId(),
        e.getChannelId(),
        e.getTitle(),
        e.getSlug(),
        e.getStatus(),
        e.getStorageKey(),
        e.getThumbnailKey(),
        e.getDurationSeconds(),
        e.getMetadata(),
        e.getErrorMessage(),
        e.getDescription(),
        e.getCategoryId(),
        e.getVisibility(),
        e.getPublishedAt(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  public static Category toDomain(CategoryEntity e) {
    return new Category(e.getId(), e.getName(), e.getSlug(), e.getCreatedAt());
  }
}
