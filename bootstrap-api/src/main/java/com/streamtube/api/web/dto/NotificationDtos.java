package com.streamtube.api.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Response payloads for the notification feed. */
public final class NotificationDtos {

  private NotificationDtos() {}

  public record NotificationResponse(
      UUID id,
      String type,
      boolean read,
      Instant createdAt,
      ActorResponse actor,
      VideoRefResponse video,
      CommentRefResponse comment) {}

  public record ActorResponse(UUID channelId, String name, String nickname) {}

  public record VideoRefResponse(UUID id, String slug, String title, String thumbnailUrl) {}

  public record CommentRefResponse(UUID id, String content) {}

  public record UnreadCountResponse(long count) {}
}
