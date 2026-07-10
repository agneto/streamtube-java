package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.PartUrlView;
import com.streamtube.domain.shared.VideoExceptions.InvalidPartNumbersException;
import com.streamtube.domain.shared.VideoExceptions.NoActiveUploadException;
import com.streamtube.domain.video.Video;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Presigned URLs for the requested parts — re-issuable anytime, which is the whole retry story: a
 * failed or expired part just asks for its URL again. Each URL signs the exact length of that
 * part (the last one is the remainder).
 */
@Service
public class IssuePartUrlsUseCase {

  private static final int MAX_PART_NUMBERS_PER_REQUEST = 100;

  private final VideoOwnership ownership;
  private final StoragePort storage;

  public IssuePartUrlsUseCase(VideoOwnership ownership, StoragePort storage) {
    this.ownership = ownership;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public List<PartUrlView> execute(UUID videoId, UUID userId, List<Integer> partNumbers) {
    Video video = ownership.requireOwned(videoId, userId);
    if (!video.hasActiveUpload()) {
      throw new NoActiveUploadException();
    }
    int totalParts = video.totalParts();
    if (partNumbers == null
        || partNumbers.isEmpty()
        || partNumbers.size() > MAX_PART_NUMBERS_PER_REQUEST
        || partNumbers.stream().anyMatch(n -> n == null || n < 1 || n > totalParts)) {
      throw new InvalidPartNumbersException();
    }
    return partNumbers.stream()
        .map(
            n -> {
              long length = partLength(video, n, totalParts);
              return new PartUrlView(
                  n,
                  storage.presignUploadPart(video.storageKey(), video.uploadId(), n, length),
                  length);
            })
        .toList();
  }

  private static long partLength(Video video, int partNumber, int totalParts) {
    if (partNumber < totalParts) {
      return video.uploadPartSize();
    }
    long remainder = video.uploadSizeBytes() % video.uploadPartSize();
    return remainder == 0 ? video.uploadPartSize() : remainder;
  }
}
