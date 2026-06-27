package com.streamtube.api.web.dto;

import java.time.Instant;

/** Shared error response shape for every endpoint. */
public record ErrorEnvelope(
    int statusCode, String code, String message, Instant timestamp, String path) {}
