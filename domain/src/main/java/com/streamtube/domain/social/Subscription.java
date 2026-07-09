package com.streamtube.domain.social;

import java.time.Instant;
import java.util.UUID;

/** A user following a channel. Pure link: uniqueness is enforced by the repository/DB. */
public record Subscription(UUID userId, UUID channelId, Instant createdAt) {}
