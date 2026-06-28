package com.streamtube.domain.video;

import java.time.Instant;
import java.util.UUID;

/** Pure domain entity for a video and its processing lifecycle. */
public class Video {

  private final UUID id;
  private final UUID channelId;
  private final String title;
  private final String slug;
  private VideoStatus status;
  private final String storageKey;
  private String thumbnailKey;
  private Double durationSeconds;
  private String metadata;
  private String errorMessage;
  private final Instant createdAt;
  private Instant updatedAt;

  public Video(
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

  /** Factory for a freshly initiated upload (awaiting the client's PUT). */
  public static Video initiate(
      UUID id, UUID channelId, String title, String slug, String storageKey, Instant now) {
    return new Video(
        id, channelId, title, slug, VideoStatus.PENDING_UPLOAD, storageKey, null, null, null, null,
        now, now);
  }

  public void markQueued(Instant now) {
    this.status = VideoStatus.QUEUED;
    this.updatedAt = now;
  }

  public void markProcessing(Instant now) {
    this.status = VideoStatus.PROCESSING;
    this.updatedAt = now;
  }

  public void markReady(Double durationSeconds, String thumbnailKey, String metadata, Instant now) {
    this.status = VideoStatus.READY;
    this.durationSeconds = durationSeconds;
    this.thumbnailKey = thumbnailKey;
    this.metadata = metadata;
    this.errorMessage = null;
    this.updatedAt = now;
  }

  public void markError(String message, Instant now) {
    this.status = VideoStatus.ERROR;
    this.errorMessage = message;
    this.updatedAt = now;
  }

  public boolean isReady() {
    return status == VideoStatus.READY;
  }

  public UUID id() {
    return id;
  }

  public UUID channelId() {
    return channelId;
  }

  public String title() {
    return title;
  }

  public String slug() {
    return slug;
  }

  public VideoStatus status() {
    return status;
  }

  public String storageKey() {
    return storageKey;
  }

  public String thumbnailKey() {
    return thumbnailKey;
  }

  public Double durationSeconds() {
    return durationSeconds;
  }

  public String metadata() {
    return metadata;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
