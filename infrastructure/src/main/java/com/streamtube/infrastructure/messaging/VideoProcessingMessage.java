package com.streamtube.infrastructure.messaging;

import java.util.UUID;

/** Queue payload for a video-processing job. */
public record VideoProcessingMessage(UUID videoId) {}
