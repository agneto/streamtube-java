package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the HLS master playlist through the API — where the visibility matrix and view counting
 * already live. Rendition URIs are rewritten to the API's playlist route; a master fetch of a
 * published video counts one view (the rendition playlists and segments never do, or one playback
 * would count N times). Videos without a ladder (pre-Phase-09 catalog) answer 404 — the frontend
 * falls back to the progressive /stream.
 */
@Service
public class GetHlsMasterUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoViewAccess access;

  public GetHlsMasterUseCase(
      VideoRepository videoRepository, StoragePort storage, VideoViewAccess access) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.access = access;
  }

  @Transactional
  public String execute(String slug, UUID viewerUserId) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    access.ensureViewable(video, viewerUserId);
    if (video.hlsMasterKey() == null) {
      throw new VideoNotFoundException();
    }
    if (video.isPublished()) {
      videoRepository.incrementViews(video.id());
    }
    String master = storage.getObjectText(video.hlsMasterKey());
    return master
        .lines()
        .map(
            line ->
                isUriLine(line) ? "/api/v1/videos/" + slug + "/hls/" + line : line)
        .collect(Collectors.joining("\n", "", "\n"));
  }

  /** Only URI lines are rewritten; every #EXT-X-* tag (and blank line) passes through verbatim. */
  static boolean isUriLine(String line) {
    return !line.isBlank() && !line.startsWith("#");
  }
}
