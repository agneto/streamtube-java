package com.streamtube.application.port.out;

/** A part already in storage for an open multipart session (as reported by the storage). */
public record UploadedPart(int partNumber, long sizeBytes, String etag) {}
