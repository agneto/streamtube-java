package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Watch-page suggestions: same-category videos that are published + PUBLIC, excluding the video
 * itself, newest publication first. An uncategorized base video falls back to the latest listed
 * videos platform-wide. The base video follows the read rule (draft → 404 for non-owners).
 */
@Service
public class GetRelatedVideosUseCase {

  static final int MAX_LIMIT = 20;

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoViewAccess access;

  public GetRelatedVideosUseCase(
      VideoRepository videoRepository, StoragePort storage, VideoViewAccess access) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public List<VideoSummaryView> execute(String slug, UUID viewerUserId, int limit) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    access.ensureViewable(video, viewerUserId);
    int clamped = Math.min(Math.max(limit, 1), MAX_LIMIT);
    List<Video> related =
        video.categoryId() == null
            ? videoRepository.findLatestListed(video.id(), clamped)
            : videoRepository.findRelatedByCategory(video.categoryId(), video.id(), clamped);
    return related.stream()
        .map(
            v ->
                VideoSummaryView.from(
                    v, v.thumbnailKey() == null ? null : storage.presignStream(v.thumbnailKey())))
        .toList();
  }
}
