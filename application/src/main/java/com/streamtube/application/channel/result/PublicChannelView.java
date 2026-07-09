package com.streamtube.application.channel.result;

import java.time.Instant;
import java.util.UUID;

/** Public channel page projection: no {@code userId} (that mapping is not public information). */
public record PublicChannelView(
    UUID id,
    String name,
    String nickname,
    String description,
    long subscribersCount,
    boolean subscribed,
    Instant createdAt) {}
