package com.streamtube.api.web.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request/response payloads for the channel endpoints. */
public final class ChannelDtos {

  private ChannelDtos() {}

  /**
   * PATCH semantics: absent/null fields are left untouched; a blank description clears it. Field
   * invariants (name length, nickname charset) are enforced by the domain entity.
   */
  public record UpdateChannelRequest(
      @Size(max = 50) String name,
      @Size(max = 50) String nickname,
      @Size(max = 5000) String description) {}

  public record ChannelInfoResponse(
      UUID id,
      UUID userId,
      String name,
      String nickname,
      String description,
      Instant createdAt,
      Instant updatedAt) {}

  /** Public channel page header: no {@code userId} (that mapping is not public information). */
  public record PublicChannelResponse(
      UUID id,
      String name,
      String nickname,
      String description,
      long subscribersCount,
      boolean subscribed,
      Instant createdAt) {}
}
