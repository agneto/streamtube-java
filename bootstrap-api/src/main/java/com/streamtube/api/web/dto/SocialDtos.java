package com.streamtube.api.web.dto;

import com.streamtube.domain.social.ReactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request/response payloads for reactions, comments and subscriptions. */
public final class SocialDtos {

  private SocialDtos() {}

  public record ReactionRequest(@NotNull ReactionType type) {}

  /** {@code parentId} present = single-level reply to a top-level comment of the same video. */
  public record CreateCommentRequest(
      @NotBlank @Size(max = 2000) String content, UUID parentId) {}

  public record CommentResponse(
      UUID id,
      UUID videoId,
      UUID parentId,
      String content,
      CommentAuthorResponse author,
      long likes,
      long dislikes,
      long repliesCount,
      Instant createdAt) {}

  public record CommentAuthorResponse(UUID channelId, String name, String nickname) {}

  public record SubscriptionResponse(
      UUID channelId,
      String name,
      String nickname,
      String description,
      long subscribersCount,
      Instant subscribedAt) {}
}
