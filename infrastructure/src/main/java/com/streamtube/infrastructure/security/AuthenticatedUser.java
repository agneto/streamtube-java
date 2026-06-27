package com.streamtube.infrastructure.security;

import java.util.UUID;

/** Principal extracted from a valid access token. */
public record AuthenticatedUser(UUID id, String email) {}
