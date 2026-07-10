package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.UploadedPartsView;
import com.streamtube.application.video.result.UploadedPartsView.UploadedPartView;
import com.streamtube.domain.shared.VideoExceptions.NoActiveUploadException;
import com.streamtube.domain.video.Video;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The resume path: which parts already made it to storage. A client that lost all local state
 * (crash, reboot, new device) re-uploads only what is missing.
 */
@Service
public class ListUploadedPartsUseCase {

  private final VideoOwnership ownership;
  private final StoragePort storage;

  public ListUploadedPartsUseCase(VideoOwnership ownership, StoragePort storage) {
    this.ownership = ownership;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public UploadedPartsView execute(UUID videoId, UUID userId) {
    Video video = ownership.requireOwned(videoId, userId);
    if (!video.hasActiveUpload()) {
      throw new NoActiveUploadException();
    }
    return new UploadedPartsView(
        video.uploadPartSize(),
        video.totalParts(),
        storage.listUploadedParts(video.storageKey(), video.uploadId()).stream()
            .map(p -> new UploadedPartView(p.partNumber(), p.sizeBytes()))
            .toList());
  }
}
