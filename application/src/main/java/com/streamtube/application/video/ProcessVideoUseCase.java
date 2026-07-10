package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.VideoAnalyzer;
import com.streamtube.application.port.out.VideoAnalyzer.ProbeResult;
import com.streamtube.application.port.out.VideoTranscoder;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker use case: probe the video, generate a thumbnail, transcode the HLS ladder, and mark it
 * READY (or ERROR).
 *
 * <p>Not a component: it depends on the worker-only {@code VideoAnalyzer}/{@code VideoTranscoder}
 * ports, so it is wired as a bean in the worker app only (the API never instantiates it).
 *
 * <p>{@link #execute} is deliberately <em>not</em> transactional: FFprobe/FFmpeg can run for
 * minutes, and a transaction spanning them would pin a DB connection for the whole run (and keep
 * the intermediate PROCESSING state invisible until the final commit). Each status write is a
 * single repository save that commits in its own short transaction, so PROCESSING becomes visible
 * immediately and no connection is held during the external work.
 */
public class ProcessVideoUseCase {

  private static final int DEFAULT_SOURCE_HEIGHT = 720;

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoAnalyzer analyzer;
  private final VideoTranscoder transcoder;
  private final Clock clock;

  public ProcessVideoUseCase(
      VideoRepository videoRepository,
      StoragePort storage,
      VideoAnalyzer analyzer,
      VideoTranscoder transcoder,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.analyzer = analyzer;
    this.transcoder = transcoder;
    this.clock = clock;
  }

  public void execute(UUID videoId) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);
    if (video.isReady()) {
      return; // idempotent on retry
    }

    // Commits right away: PROCESSING is visible while the (long) analysis below runs. A crash
    // mid-analysis leaves the video PROCESSING; redelivery re-enters here and re-marks it.
    video.markProcessing(clock.instant());
    videoRepository.save(video);

    // Long-running external work — no transaction (and no DB connection) held during this block.
    String inputUrl = storage.presignInternal(video.storageKey());
    ProbeResult probe = analyzer.probe(inputUrl);
    byte[] thumbnail = analyzer.extractThumbnail(inputUrl);

    String thumbnailKey = "thumbnails/" + video.slug() + ".jpg";
    storage.putObject(thumbnailKey, thumbnail, "image/jpeg");

    String hlsMasterKey = transcodeAndUploadHls(video, inputUrl, probe);

    video.markReady(
        probe.durationSeconds(), thumbnailKey, probe.rawJson(), hlsMasterKey, clock.instant());
    videoRepository.save(video);
  }

  /** Renders the ladder to a temp workspace, uploads it under {@code hls/{slug}/}, cleans up. */
  private String transcodeAndUploadHls(Video video, String inputUrl, ProbeResult probe) {
    int sourceHeight = probe.height() != null ? probe.height() : DEFAULT_SOURCE_HEIGHT;
    String prefix = "hls/" + video.slug() + "/";
    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("hls-" + video.slug());
      transcoder.transcodeToHls(inputUrl, workDir, sourceHeight);
      uploadTree(workDir, prefix);
      return prefix + "master.m3u8";
    } catch (IOException e) {
      throw new IllegalStateException("Failed to stage HLS ladder", e);
    } finally {
      deleteRecursively(workDir);
    }
  }

  private void uploadTree(Path workDir, String prefix) throws IOException {
    try (Stream<Path> files = Files.walk(workDir)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        String relative = workDir.relativize(file).toString().replace('\\', '/');
        String contentType =
            relative.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
        storage.putObject(prefix + relative, Files.readAllBytes(file), contentType);
      }
    }
  }

  private static void deleteRecursively(Path dir) {
    if (dir == null) {
      return;
    }
    try (Stream<Path> files = Files.walk(dir)) {
      files
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // best-effort cleanup of the temp workspace
                }
              });
    } catch (IOException ignored) {
      // best-effort cleanup of the temp workspace
    }
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
