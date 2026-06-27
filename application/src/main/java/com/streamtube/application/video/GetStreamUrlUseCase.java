package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotReadyException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns a presigned streaming URL for a ready video. */
@Service
public class GetStreamUrlUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;

  public GetStreamUrlUseCase(VideoRepository videoRepository, StoragePort storage) {
    this.videoRepository = videoRepository;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public String execute(String slug) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    if (!video.isReady()) {
      throw new VideoNotReadyException();
    }
    return storage.presignStream(video.storageKey());
  }
}
