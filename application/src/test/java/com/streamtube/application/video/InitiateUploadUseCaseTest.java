package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.SlugGenerator;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.InitiateUploadResult;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.VideoStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class InitiateUploadUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private SlugGenerator slugGenerator;
  private InitiateUploadUseCase useCase;
  private UUID userId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    slugGenerator = Mockito.mock(SlugGenerator.class);
    useCase =
        new InitiateUploadUseCase(
            videos, channels, storage, slugGenerator, Clock.fixed(NOW, ZoneOffset.UTC));

    userId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(Optional.of(Channel.createForUser(channelId, userId, "name", "nick", NOW)));
    when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void createsDraftVideoAndReturnsPresignedUploadUrl() {
    when(slugGenerator.generate()).thenReturn("slug123");
    when(videos.existsBySlug("slug123")).thenReturn(false);
    when(storage.presignUpload("videos/slug123")).thenReturn("http://upload-url");

    InitiateUploadResult result = useCase.execute(userId, "My Video");

    assertThat(result.slug()).isEqualTo("slug123");
    assertThat(result.uploadUrl()).isEqualTo("http://upload-url");

    ArgumentCaptor<Video> savedVideo = ArgumentCaptor.forClass(Video.class);
    verify(videos).save(savedVideo.capture());
    assertThat(savedVideo.getValue().status()).isEqualTo(VideoStatus.PENDING_UPLOAD);
    assertThat(savedVideo.getValue().channelId()).isEqualTo(channelId);
    assertThat(savedVideo.getValue().storageKey()).isEqualTo("videos/slug123");
  }

  @Test
  void retriesSlugGenerationOnCollision() {
    when(slugGenerator.generate()).thenReturn("taken", "free");
    when(videos.existsBySlug("taken")).thenReturn(true);
    when(videos.existsBySlug("free")).thenReturn(false);
    when(storage.presignUpload("videos/free")).thenReturn("http://upload-url");

    InitiateUploadResult result = useCase.execute(userId, "My Video");

    assertThat(result.slug()).isEqualTo("free");
  }

  @Test
  void failsAfterExhaustingSlugAttempts() {
    when(slugGenerator.generate()).thenReturn("always-taken");
    when(videos.existsBySlug("always-taken")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(userId, "My Video"))
        .isInstanceOf(IllegalStateException.class);
    verify(videos, never()).save(any());
  }

  @Test
  void failsWhenUserHasNoChannel() {
    when(channels.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId, "My Video"))
        .isInstanceOf(IllegalStateException.class);
    verify(videos, never()).save(any());
  }
}
