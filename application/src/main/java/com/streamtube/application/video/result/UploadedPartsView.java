package com.streamtube.application.video.result;

import java.util.List;

/** Resume view: what already made it to storage for the active multipart session. */
public record UploadedPartsView(
    long partSizeBytes, int totalParts, List<UploadedPartView> uploaded) {

  public record UploadedPartView(int partNumber, long sizeBytes) {}
}
