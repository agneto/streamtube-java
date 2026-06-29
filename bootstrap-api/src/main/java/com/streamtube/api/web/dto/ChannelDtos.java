package com.streamtube.api.web.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request/response payloads for the channel endpoints. */
public final class ChannelDtos {

  private ChannelDtos() {}

  /** Description is optional: null/blank clears it; max 5000 chars (domain invariant). */
  public record UpdateChannelRequest(@Size(max = 5000) String description) {}

  public record ChannelInfoResponse(
      UUID id,
      UUID userId,
      String name,
      String nickname,
      String description,
      Instant createdAt,
      Instant updatedAt) {}
}
