package com.streamtube.application.auth.result;

public record TokenPair(String accessToken, long expiresInSeconds, String refreshToken) {}
