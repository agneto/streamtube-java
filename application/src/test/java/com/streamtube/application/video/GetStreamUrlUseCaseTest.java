package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotReadyException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Covers view counting on the stream path: published counts, draft preview does not. */
class GetStreamUrlUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private GetStreamUrlUseCase useCase;
  private UUID ownerUserId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    StoragePort storage = Mockito.mock(StoragePort.class);
    when(storage.presignStream("videos/slug123")).thenReturn("http://minio/stream");
    useCase = new GetStreamUrlUseCase(videos, storage, new VideoViewAccess(channels));

    ownerUserId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(ownerUserId))
        .thenReturn(
            Optional.of(Channel.createForUser(channelId, ownerUserId, "name", "nick", NOW)));
  }

  private Video readyDraft() {
    Video v = Video.initiate(UUID.randomUUID(), channelId, "T", "slug123", "videos/slug123", NOW);
    v.markReady(12.5, null, "{}", NOW);
    when(videos.findBySlug("slug123")).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  void publishedStreamIncrementsViewsThroughThePort() {
    Video v = readyDraft();
    v.publish(NOW);

    String url = useCase.execute("slug123", null);

    assertThat(url).isEqualTo("http://minio/stream");
    verify(videos).incrementViews(v.id());
  }

  @Test
  void ownerDraftPreviewPlaysButDoesNotCount() {
    readyDraft();

    String url = useCase.execute("slug123", ownerUserId);

    assertThat(url).isEqualTo("http://minio/stream");
    verify(videos, never()).incrementViews(any());
  }

  @Test
  void notReadyVideoIsRejectedWithoutCounting() {
    Video v = Video.initiate(UUID.randomUUID(), channelId, "T", "slug123", "videos/slug123", NOW);
    when(videos.findBySlug("slug123")).thenReturn(Optional.of(v));

    assertThatThrownBy(() -> useCase.execute("slug123", ownerUserId))
        .isInstanceOf(VideoNotReadyException.class);
    verify(videos, never()).incrementViews(any());
  }

  @Test
  void unknownSlugIs404() {
    when(videos.findBySlug("nope")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute("nope", null))
        .isInstanceOf(VideoNotFoundException.class);
  }
}
