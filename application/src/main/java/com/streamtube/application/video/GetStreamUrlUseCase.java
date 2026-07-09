package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotReadyException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a presigned streaming URL for a ready video the viewer may watch, counting the view.
 *
 * <p>Only published videos accumulate views (the owner previewing a draft is not audience), via a
 * single atomic increment in the same transaction — hence not read-only.
 */
@Service
public class GetStreamUrlUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoViewAccess access;

  public GetStreamUrlUseCase(
      VideoRepository videoRepository, StoragePort storage, VideoViewAccess access) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.access = access;
  }

  @Transactional
  public String execute(String slug, UUID viewerUserId) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    access.ensureViewable(video, viewerUserId);
    if (!video.isReady()) {
      throw new VideoNotReadyException();
    }
    if (video.isPublished()) {
      videoRepository.incrementViews(video.id());
    }
    return storage.presignStream(video.storageKey());
  }
}
