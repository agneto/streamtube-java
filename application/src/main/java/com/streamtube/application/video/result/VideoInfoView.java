package com.streamtube.application.video.result;

import java.time.Instant;
import java.util.UUID;

public record VideoInfoView(
    UUID id,
    String slug,
    String title,
    String status,
    String thumbnailUrl,
    Double durationSeconds,
    UUID channelId,
    Instant createdAt) {}
