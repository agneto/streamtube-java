package com.streamtube.application.channel.result;

import java.time.Instant;
import java.util.UUID;

public record ChannelInfoView(
        UUID id,
        UUID userId,
        String name,
        String nickname,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
