package com.streamtube.infrastructure.persistence.entity;

import com.streamtube.domain.video.VideoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for a video. {@code channel_id} is a plain UUID column (DB-level FK only, no JPA
 * association), so the worker's persistence unit needs only this entity.
 */
@Entity
@Table(name = "videos")
public class VideoEntity {

  @Id private UUID id;

  @Column(name = "channel_id", nullable = false)
  private UUID channelId;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, unique = true, length = 16)
  private String slug;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VideoStatus status;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "thumbnail_key", length = 500)
  private String thumbnailKey;

  @Column(name = "duration_seconds")
  private Double durationSeconds;

  @Column(columnDefinition = "text")
  private String metadata;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected VideoEntity() {}

  public VideoEntity(
      UUID id,
      UUID channelId,
      String title,
      String slug,
      VideoStatus status,
      String storageKey,
      String thumbnailKey,
      Double durationSeconds,
      String metadata,
      String errorMessage,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.channelId = channelId;
    this.title = title;
    this.slug = slug;
    this.status = status;
    this.storageKey = storageKey;
    this.thumbnailKey = thumbnailKey;
    this.durationSeconds = durationSeconds;
    this.metadata = metadata;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public String getTitle() {
    return title;
  }

  public String getSlug() {
    return slug;
  }

  public VideoStatus getStatus() {
    return status;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getThumbnailKey() {
    return thumbnailKey;
  }

  public Double getDurationSeconds() {
    return durationSeconds;
  }

  public String getMetadata() {
    return metadata;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
