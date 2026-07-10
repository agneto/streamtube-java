package com.streamtube.application.video.result;

import java.util.UUID;

/** Outcome of opening a multipart upload: the client slices the file by {@code partSizeBytes}. */
public record InitiateMultipartResult(UUID id, String slug, long partSizeBytes, int totalParts) {}
