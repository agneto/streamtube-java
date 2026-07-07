package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotReadyException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns a presigned download URL (forces attachment) for a ready video the viewer may watch. */
@Service
public class GetDownloadUrlUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoViewAccess access;

  public GetDownloadUrlUseCase(
      VideoRepository videoRepository, StoragePort storage, VideoViewAccess access) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public String execute(String slug, UUID viewerUserId) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    access.ensureViewable(video, viewerUserId);
    if (!video.isReady()) {
      throw new VideoNotReadyException();
    }
    return storage.presignDownload(video.storageKey(), video.title() + ".mp4");
  }
}
