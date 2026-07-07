package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns video info, including a presigned thumbnail URL when available. Drafts are owner-only
 * (404 for anyone else); published videos are open regardless of visibility.
 */
@Service
public class GetVideoInfoUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoViewAccess access;

  public GetVideoInfoUseCase(
      VideoRepository videoRepository, StoragePort storage, VideoViewAccess access) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public VideoInfoView execute(String slug, UUID viewerUserId) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    access.ensureViewable(video, viewerUserId);
    String thumbnailUrl =
        video.thumbnailKey() == null ? null : storage.presignStream(video.thumbnailKey());
    return VideoInfoView.from(video, thumbnailUrl);
  }
}
