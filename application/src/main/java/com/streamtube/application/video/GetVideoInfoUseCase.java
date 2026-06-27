package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns public video info, including a presigned thumbnail URL when available. */
@Service
public class GetVideoInfoUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;

  public GetVideoInfoUseCase(VideoRepository videoRepository, StoragePort storage) {
    this.videoRepository = videoRepository;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public VideoInfoView execute(String slug) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    String thumbnailUrl =
        video.thumbnailKey() == null ? null : storage.presignStream(video.thumbnailKey());
    return new VideoInfoView(
        video.id(),
        video.slug(),
        video.title(),
        video.status().name(),
        thumbnailUrl,
        video.durationSeconds(),
        video.channelId(),
        video.createdAt());
  }
}
