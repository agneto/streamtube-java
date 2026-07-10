package com.streamtube.application.video.result;

/** Presigned URL for one part; {@code contentLengthBytes} is signed into it (exact match). */
public record PartUrlView(int partNumber, String url, long contentLengthBytes) {}
