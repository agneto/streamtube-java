package com.streamtube.domain.video;

import com.streamtube.domain.shared.VideoExceptions.InvalidVideoDescriptionException;
import com.streamtube.domain.shared.VideoExceptions.InvalidVideoTitleException;
import com.streamtube.domain.shared.VideoExceptions.VideoStatusConflictException;
import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain entity for a video and its processing lifecycle.
 *
 * <p>Publication is orthogonal to processing: {@code publishedAt == null} means draft (owner-only
 * on every read path); once published, {@link Visibility} decides listing exposure. Publishing
 * requires the processing lifecycle to have reached {@code READY}.
 */
public class Video {

  private static final int MAX_DESCRIPTION_LENGTH = 5000;

  private final UUID id;
  private final UUID channelId;
  private String title;
  private final String slug;
  private VideoStatus status;
  private final String storageKey;
  private String thumbnailKey;
  private Double durationSeconds;
  private String metadata;
  private String errorMessage;
  private String description;
  private UUID categoryId;
  private Visibility visibility;
  private Instant publishedAt;
  private final long viewsCount;
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

  /** Factory for a freshly initiated upload (awaiting the client's PUT): a PUBLIC draft. */
  public static Video initiate(
      UUID id, UUID channelId, String title, String slug, String storageKey, Instant now) {
    return new Video(
        id, channelId, title, slug, VideoStatus.PENDING_UPLOAD, storageKey, null, null, null, null,
        null, null, Visibility.PUBLIC, null, 0L, now, now);
  }

  /**
   * Renames the video. Domain invariant: the title must be non-blank and at most 255 chars. This
   * rule belongs here (it is true for any video, regardless of the use case calling it).
   */
  public void rename(String newTitle, Instant now) {
    if (newTitle == null || newTitle.isBlank() || newTitle.trim().length() > 255) {
      throw new InvalidVideoTitleException();
    }
    this.title = newTitle.trim();
    this.updatedAt = now;
  }

  /** Updates the description. Optional: {@code null} or blank clears it; at most 5000 chars. */
  public void describe(String newDescription, Instant now) {
    if (newDescription != null && newDescription.length() > MAX_DESCRIPTION_LENGTH) {
      throw new InvalidVideoDescriptionException();
    }
    this.description = (newDescription == null || newDescription.isBlank()) ? null : newDescription;
    this.updatedAt = now;
  }

  /** Assigns a platform category (referential validity is checked by the use case). */
  public void categorize(UUID newCategoryId, Instant now) {
    this.categoryId = newCategoryId;
    this.updatedAt = now;
  }

  public void changeVisibility(Visibility newVisibility, Instant now) {
    this.visibility = newVisibility;
    this.updatedAt = now;
  }

  /** Draft → published. Requires READY; republishing an already-published video is a no-op. */
  public void publish(Instant now) {
    if (isPublished()) {
      return;
    }
    if (!isReady()) {
      throw new VideoStatusConflictException();
    }
    this.publishedAt = now;
    this.updatedAt = now;
  }

  /** Swaps the thumbnail for a custom one; only meaningful once processing produced a READY video. */
  public void changeThumbnail(String newThumbnailKey, Instant now) {
    if (!isReady()) {
      throw new VideoStatusConflictException();
    }
    this.thumbnailKey = newThumbnailKey;
    this.updatedAt = now;
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

  public boolean isPublished() {
    return publishedAt != null;
  }

  /** Read rule for info/stream/download: published videos are open, drafts are owner-only. */
  public boolean isAccessibleBy(UUID viewerChannelId) {
    return isPublished() || channelId.equals(viewerChannelId);
  }

  public boolean isListedPublicly() {
    return isPublished() && visibility == Visibility.PUBLIC;
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

  public String description() {
    return description;
  }

  public UUID categoryId() {
    return categoryId;
  }

  public Visibility visibility() {
    return visibility;
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  /**
   * Total playback views. Read-only here: increments are an atomic SQL operation behind the
   * repository port, or concurrent viewers would lose updates. No dedup by design — reloads count
   * again.
   */
  public long viewsCount() {
    return viewsCount;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
