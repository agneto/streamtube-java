package com.streamtube.infrastructure.persistence.entity;

import com.streamtube.domain.video.VideoStatus;
import com.streamtube.domain.video.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence model for a video. {@code channel_id} and {@code category_id} are plain UUID columns
 * (DB-level FK only, no JPA association), so the worker's persistence unit needs only this entity.
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

  // Raw ffprobe JSON stored as jsonb: Postgres validates it on write and future queries can
  // reach into it (codec, resolution) without parsing text.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String metadata;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "category_id")
  private UUID categoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Visibility visibility;

  @Column(name = "published_at")
  private Instant publishedAt;

  // updatable = false: only the atomic "views_count + 1" statement may change the counter — a
  // regular save() flushing a stale in-memory value would silently erase concurrent views.
  @Column(name = "views_count", nullable = false, updatable = false)
  private long viewsCount;

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
      String description,
      UUID categoryId,
      Visibility visibility,
      Instant publishedAt,
      long viewsCount,
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
    this.description = description;
    this.categoryId = categoryId;
    this.visibility = visibility;
    this.publishedAt = publishedAt;
    this.viewsCount = viewsCount;
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

  public String getDescription() {
    return description;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public long getViewsCount() {
    return viewsCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
