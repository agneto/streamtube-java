package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StorageCleanupQueue;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** The exact prefix set, the abort-before-delete ordering, and the ownership rules. */
class DeleteVideoUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private StorageCleanupQueue cleanups;
  private DeleteVideoUseCase useCase;
  private UUID ownerUserId;
  private UUID channelId;
  private UUID videoId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    cleanups = Mockito.mock(StorageCleanupQueue.class);
    useCase =
        new DeleteVideoUseCase(new VideoOwnership(videos, channels), videos, storage, cleanups);

    ownerUserId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    videoId = UUID.randomUUID();
    when(channels.findByUserId(ownerUserId))
        .thenReturn(
            Optional.of(Channel.createForUser(channelId, ownerUserId, "name", "nick", NOW)));
  }

  private Video video() {
    Video v = Video.initiate(videoId, channelId, "T", "slug1234567", "videos/slug1234567", NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  void enqueuesEveryArtifactFamilyAndDeletesTheRow() {
    video();

    useCase.execute(videoId, ownerUserId);

    verify(cleanups).enqueue("videos/slug1234567");
    verify(cleanups).enqueue("thumbnails/slug1234567");
    verify(cleanups).enqueue("hls/slug1234567/");
    verify(videos).delete(any());
    // no in-tx storage deletion, ever — the outbox owns it
    verify(storage, never()).deleteObject(anyString());
    verify(storage, never()).deleteObjectsByPrefix(anyString());
  }

  @Test
  void abortsAnActiveMultipartSessionBeforeTheRowVanishes() {
    Video v = video();
    v.beginMultipartUpload("up-1", 1000L, 100L, NOW);

    useCase.execute(videoId, ownerUserId);

    InOrder order = inOrder(storage, videos);
    order.verify(storage).abortMultipartUpload("videos/slug1234567", "up-1");
    order.verify(videos).delete(any());
  }

  @Test
  void nonOwnerIsForbidden() {
    video();
    UUID otherUserId = UUID.randomUUID();
    when(channels.findByUserId(otherUserId))
        .thenReturn(
            Optional.of(
                Channel.createForUser(UUID.randomUUID(), otherUserId, "other", "other", NOW)));

    assertThatThrownBy(() -> useCase.execute(videoId, otherUserId))
        .isInstanceOf(ForbiddenVideoAccessException.class);
    verify(videos, never()).delete(any());
    verify(cleanups, never()).enqueue(anyString());
  }

  @Test
  void secondDeleteIs404() {
    when(videos.findById(videoId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId))
        .isInstanceOf(VideoNotFoundException.class);
  }
}
