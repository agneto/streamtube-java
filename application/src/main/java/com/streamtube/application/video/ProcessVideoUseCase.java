package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.VideoAnalyzer;
import com.streamtube.application.port.out.VideoAnalyzer.ProbeResult;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker use case: probe the video, generate a thumbnail, and mark it READY (or ERROR).
 *
 * <p>Not a component: it depends on the worker-only {@code VideoAnalyzer} port, so it is wired as a
 * bean in the worker app only (the API never instantiates it).
 */
public class ProcessVideoUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoAnalyzer analyzer;
  private final Clock clock;

  public ProcessVideoUseCase(
      VideoRepository videoRepository,
      StoragePort storage,
      VideoAnalyzer analyzer,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.analyzer = analyzer;
    this.clock = clock;
  }

  @Transactional
  public void execute(UUID videoId) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);
    if (video.isReady()) {
      return; // idempotent on retry
    }

    video.markProcessing(clock.instant());
    videoRepository.save(video);

    String inputUrl = storage.presignInternal(video.storageKey());
    ProbeResult probe = analyzer.probe(inputUrl);
    byte[] thumbnail = analyzer.extractThumbnail(inputUrl);

    String thumbnailKey = "thumbnails/" + video.slug() + ".jpg";
    storage.putObject(thumbnailKey, thumbnail, "image/jpeg");

    video.markReady(probe.durationSeconds(), thumbnailKey, probe.rawJson(), clock.instant());
    videoRepository.save(video);
  }

  /** Marks the video as failed after the listener exhausts its retries. */
  @Transactional
  public void markFailed(UUID videoId, String message) {
    videoRepository
        .findById(videoId)
        .ifPresent(
            video -> {
              video.markError(message, clock.instant());
              videoRepository.save(video);
            });
  }
}
