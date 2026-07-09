package com.streamtube.domain.social;

import com.streamtube.domain.shared.SocialExceptions.InvalidCommentContentException;
import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain entity for a comment on a video. Nesting is single-level (YouTube-style): a reply's
 * {@code parentId} points at a top-level comment; replying to a reply is rejected by the use case.
 * Comments are immutable after creation (no editing in the reference plan) and counters are
 * read-only here — increments are atomic SQL behind the repository ports.
 */
public class Comment {

  private static final int MAX_CONTENT_LENGTH = 2000;

  private final UUID id;
  private final UUID videoId;
  private final UUID userId;
  private final UUID parentId;
  private final String content;
  private final long likesCount;
  private final long dislikesCount;
  private final long repliesCount;
  private final Instant createdAt;

  public Comment(
      UUID id,
      UUID videoId,
      UUID userId,
      UUID parentId,
      String content,
      long likesCount,
      long dislikesCount,
      long repliesCount,
      Instant createdAt) {
    this.id = id;
    this.videoId = videoId;
    this.userId = userId;
    this.parentId = parentId;
    this.content = content;
    this.likesCount = likesCount;
    this.dislikesCount = dislikesCount;
    this.repliesCount = repliesCount;
    this.createdAt = createdAt;
  }

  /** Factory for a new comment or reply. Invariant: content non-blank, at most 2000 chars. */
  public static Comment create(
      UUID id, UUID videoId, UUID userId, UUID parentId, String content, Instant now) {
    if (content == null || content.isBlank() || content.trim().length() > MAX_CONTENT_LENGTH) {
      throw new InvalidCommentContentException();
    }
    return new Comment(id, videoId, userId, parentId, content.trim(), 0L, 0L, 0L, now);
  }

  public boolean isReply() {
    return parentId != null;
  }

  public boolean isAuthoredBy(UUID otherUserId) {
    return userId.equals(otherUserId);
  }

  public UUID id() {
    return id;
  }

  public UUID videoId() {
    return videoId;
  }

  public UUID userId() {
    return userId;
  }

  public UUID parentId() {
    return parentId;
  }

  public String content() {
    return content;
  }

  public long likesCount() {
    return likesCount;
  }

  public long dislikesCount() {
    return dislikesCount;
  }

  public long repliesCount() {
    return repliesCount;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
