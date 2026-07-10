package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.VideoAnalyzer;
import com.streamtube.application.port.out.VideoAnalyzer.ProbeResult;
import com.streamtube.application.port.out.VideoTranscoder;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.VideoStatus;
import com.streamtube.domain.video.Visibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProcessVideoUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private StoragePort storage;
  private VideoAnalyzer analyzer;
  private VideoTranscoder transcoder;
  private ProcessVideoUseCase useCase;
  private UUID videoId;

  @BeforeEach
  void setUp() throws Exception {
    videos = Mockito.mock(VideoRepository.class);
    storage = Mockito.mock(StoragePort.class);
    analyzer = Mockito.mock(VideoAnalyzer.class);
    transcoder = Mockito.mock(VideoTranscoder.class);
    useCase =
        new ProcessVideoUseCase(
            videos, storage, analyzer, transcoder, Clock.fixed(NOW, ZoneOffset.UTC));
    videoId = UUID.randomUUID();
    when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
    // Fake ladder: drop a master playlist into the workspace the use case hands over.
    when(transcoder.transcodeToHls(any(), any(), Mockito.anyInt()))
        .thenAnswer(
            inv -> {
              java.nio.file.Path workDir = inv.getArgument(1);
              java.nio.file.Files.writeString(workDir.resolve("master.m3u8"), "#EXTM3U\n");
              return List.of("360p");
            });
  }

  private Video pendingVideo() {
    return new Video(
        videoId, UUID.randomUUID(), "Title", "slug123", VideoStatus.QUEUED, "videos/slug123",
        null, null, null, null, null, null, Visibility.PUBLIC, null, 0L, 0L, 0L, 0L, null, null,
        null, null, NOW, NOW);
  }

  @Test
  void processesVideoToReady() {
    Video video = pendingVideo();
    when(videos.findById(videoId)).thenReturn(Optional.of(video));
    when(storage.presignInternal("videos/slug123")).thenReturn("http://minio/internal");
    when(analyzer.probe("http://minio/internal")).thenReturn(new ProbeResult(12.5, 720, "{\"ok\":1}"));
    when(analyzer.extractThumbnail("http://minio/internal")).thenReturn(new byte[] {1, 2, 3});

    // record the status persisted by each save, in order
    List<VideoStatus> savedStatuses = new ArrayList<>();
    when(videos.save(any()))
        .thenAnswer(
            inv -> {
              Video saved = inv.getArgument(0);
              savedStatuses.add(saved.status());
              return saved;
            });

    useCase.execute(videoId);

    // PROCESSING is persisted before the analysis result: it must be visible while ffmpeg runs
    assertThat(savedStatuses).containsExactly(VideoStatus.PROCESSING, VideoStatus.READY);
    assertThat(video.durationSeconds()).isEqualTo(12.5);
    assertThat(video.thumbnailKey()).isEqualTo("thumbnails/slug123.jpg");
    verify(storage).putObject(eq("thumbnails/slug123.jpg"), any(), eq("image/jpeg"));
    // the fake ladder was uploaded under the video's prefix and recorded on the entity
    assertThat(video.hlsMasterKey()).isEqualTo("hls/slug123/master.m3u8");
    verify(storage)
        .putObject(eq("hls/slug123/master.m3u8"), any(), eq("application/vnd.apple.mpegurl"));
  }

  @Test
  void reprocessesVideoStuckInProcessingOnRedelivery() {
    Video stuck =
        new Video(
            videoId, UUID.randomUUID(), "T", "slug123", VideoStatus.PROCESSING, "videos/slug123",
            null, null, null, null, null, null, Visibility.PUBLIC, null, 0L, 0L, 0L, 0L, null,
            null, null, null, NOW, NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(stuck));
    when(storage.presignInternal("videos/slug123")).thenReturn("http://minio/internal");
    when(analyzer.probe("http://minio/internal")).thenReturn(new ProbeResult(5.0, 480, "{}"));
    when(analyzer.extractThumbnail("http://minio/internal")).thenReturn(new byte[] {1});

    useCase.execute(videoId);

    assertThat(stuck.status()).isEqualTo(VideoStatus.READY);
  }

  @Test
  void isIdempotentWhenAlreadyReady() {
    Video ready =
        new Video(
            videoId, UUID.randomUUID(), "T", "slug123", VideoStatus.READY, "videos/slug123",
            "thumbnails/slug123.jpg", 10.0, "{}", null, null, null, Visibility.PUBLIC, null, 0L,
            0L, 0L, 0L, null, null, null, null, NOW, NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(ready));

    useCase.execute(videoId);

    verify(analyzer, never()).probe(any());
    verify(storage, never()).putObject(any(), any(), any());
  }

  @Test
  void markFailedSetsErrorStatus() {
    Video video = pendingVideo();
    when(videos.findById(videoId)).thenReturn(Optional.of(video));

    useCase.markFailed(videoId, "boom");

    assertThat(video.status()).isEqualTo(VideoStatus.ERROR);
    assertThat(video.errorMessage()).isEqualTo("boom");
  }
}
