package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.VideoProcessingPublisher;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.UploadNotCompletedException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoStatusConflictException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.VideoStatus;
import com.streamtube.domain.video.Visibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CompleteUploadUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private VideoProcessingPublisher publisher;
  private CompleteUploadUseCase useCase;
  private UUID videoId;
  private UUID userId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    publisher = Mockito.mock(VideoProcessingPublisher.class);
    useCase =
        new CompleteUploadUseCase(
            new VideoOwnership(videos, channels),
            storage,
            new QueueForProcessing(videos, publisher),
            Clock.fixed(NOW, ZoneOffset.UTC));

    videoId = UUID.randomUUID();
    userId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(Optional.of(Channel.createForUser(channelId, userId, "name", "nick", NOW)));
    when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Video video(UUID owningChannel, VideoStatus status) {
    return new Video(
        videoId, owningChannel, "T", "slug123", status, "videos/slug123", null, null, null, null,
        null, null, Visibility.PUBLIC, null, 0L, 0L, 0L, 0L, null, null, null, NOW, NOW);
  }

  @Test
  void marksQueuedAndPublishesProcessingJob() {
    Video video = video(channelId, VideoStatus.PENDING_UPLOAD);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));
    when(storage.objectExists("videos/slug123")).thenReturn(true);

    useCase.execute(videoId, userId);

    assertThat(video.status()).isEqualTo(VideoStatus.QUEUED);
    verify(videos).save(video);
    verify(publisher).publish(videoId);
  }

  @Test
  void rejectsUnknownVideo() {
    when(videos.findById(videoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(VideoNotFoundException.class);
    verify(publisher, never()).publish(any());
  }

  @Test
  void rejectsUserWhoDoesNotOwnTheVideo() {
    when(videos.findById(videoId))
        .thenReturn(Optional.of(video(UUID.randomUUID(), VideoStatus.PENDING_UPLOAD)));

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(ForbiddenVideoAccessException.class);
    verify(publisher, never()).publish(any());
  }

  @Test
  void rejectsUserWithoutChannel() {
    when(videos.findById(videoId))
        .thenReturn(Optional.of(video(channelId, VideoStatus.PENDING_UPLOAD)));
    when(channels.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(ForbiddenVideoAccessException.class);
    verify(publisher, never()).publish(any());
  }

  @Test
  void rejectsVideoNotPendingUpload() {
    when(videos.findById(videoId)).thenReturn(Optional.of(video(channelId, VideoStatus.QUEUED)));

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(VideoStatusConflictException.class);
    verify(publisher, never()).publish(any());
  }

  @Test
  void rejectsWhenObjectIsMissingInStorage() {
    Video video = video(channelId, VideoStatus.PENDING_UPLOAD);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));
    when(storage.objectExists("videos/slug123")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(UploadNotCompletedException.class);
    assertThat(video.status()).isEqualTo(VideoStatus.PENDING_UPLOAD);
    verify(publisher, never()).publish(any());
  }
}
