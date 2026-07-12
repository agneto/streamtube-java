package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StorageCleanupQueue;
import com.streamtube.application.port.out.StorageCleanupQueue.PendingCleanup;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Stale-draft purge rules and the drain's remove-only-after-success guarantee. */
class LifecycleSweeperUseCasesTest {

  private static final Instant NOW = Instant.parse("2026-01-10T00:00:00Z");

  private VideoRepository videos;
  private StoragePort storage;
  private StorageCleanupQueue cleanups;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    storage = Mockito.mock(StoragePort.class);
    cleanups = Mockito.mock(StorageCleanupQueue.class);
  }

  @Test
  void purgeRetiresStaleDraftsWithTheCorrectCutoff() {
    Video stale =
        Video.initiate(
            UUID.randomUUID(), UUID.randomUUID(), "T", "old12345678", "videos/old12345678", NOW);
    stale.beginMultipartUpload("up-9", 1000L, 100L, NOW);
    when(videos.findStalePendingUploads(any(), anyInt())).thenReturn(List.of(stale));
    var useCase =
        new PurgeStaleUploadsUseCase(
            videos, storage, cleanups, Clock.fixed(NOW, ZoneOffset.UTC), 7);

    int purged = useCase.execute();

    assertThat(purged).isEqualTo(1);
    // cutoff = now - 7 days, exactly
    verify(videos).findStalePendingUploads(Instant.parse("2026-01-03T00:00:00Z"), 100);
    verify(storage).abortMultipartUpload("videos/old12345678", "up-9");
    verify(cleanups).enqueue("videos/old12345678");
    verify(cleanups).enqueue("thumbnails/old12345678");
    verify(cleanups).enqueue("hls/old12345678/");
    verify(videos).delete(stale);
  }

  @Test
  void drainRemovesEntriesOnlyAfterTheStorageConfirmed() {
    UUID okId = UUID.randomUUID();
    UUID failId = UUID.randomUUID();
    UUID neverReachedId = UUID.randomUUID();
    when(cleanups.due(100))
        .thenReturn(
            List.of(
                new PendingCleanup(okId, "videos/a"),
                new PendingCleanup(failId, "videos/b"),
                new PendingCleanup(neverReachedId, "videos/c")));
    doThrow(new IllegalStateException("storage down"))
        .when(storage)
        .deleteObjectsByPrefix("videos/b");
    var useCase = new ProcessStorageCleanupsUseCase(cleanups, storage);

    assertThatThrownBy(useCase::execute).isInstanceOf(IllegalStateException.class);

    verify(cleanups).remove(okId); // succeeded before the failure
    verify(cleanups, never()).remove(failId); // stays queued -> retries next tick
    verify(cleanups, never()).remove(neverReachedId);
  }

  @Test
  void drainProcessesTheWholeBatchWhenStorageCooperates() {
    UUID id = UUID.randomUUID();
    when(cleanups.due(100)).thenReturn(List.of(new PendingCleanup(id, "hls/x/")));
    var useCase = new ProcessStorageCleanupsUseCase(cleanups, storage);

    assertThat(useCase.execute()).isEqualTo(1);
    verify(storage).deleteObjectsByPrefix("hls/x/");
    verify(cleanups).remove(id);
  }
}
