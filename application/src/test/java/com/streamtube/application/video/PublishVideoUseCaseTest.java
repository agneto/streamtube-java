package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.VideoStatusConflictException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.Visibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PublishVideoUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private NotificationRepository notifications;
  private PublishVideoUseCase useCase;
  private UUID videoId;
  private UUID userId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    notifications = Mockito.mock(NotificationRepository.class);
    StoragePort storage = Mockito.mock(StoragePort.class);
    useCase =
        new PublishVideoUseCase(
            videos, channels, notifications, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    videoId = UUID.randomUUID();
    userId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(Optional.of(Channel.createForUser(channelId, userId, "name", "nick", NOW)));
    when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Video readyVideo(UUID owningChannel) {
    Video v = Video.initiate(videoId, owningChannel, "T", "slug123", "videos/slug123", NOW);
    v.markReady(12.5, "thumbnails/slug123.jpg", "{}", NOW);
    return v;
  }

  @Test
  void publishesReadyDraft() {
    when(videos.findById(videoId)).thenReturn(Optional.of(readyVideo(channelId)));

    VideoInfoView view = useCase.execute(videoId, userId);

    assertThat(view.publishedAt()).isEqualTo(NOW);
  }

  @Test
  void firstPublicPublishFansOutNewVideo() {
    when(videos.findById(videoId)).thenReturn(Optional.of(readyVideo(channelId)));

    useCase.execute(videoId, userId);

    verify(notifications).fanOutNewVideo(channelId, videoId, NOW);
  }

  @Test
  void unlistedPublishDoesNotFanOut() {
    Video video = readyVideo(channelId);
    video.changeVisibility(Visibility.UNLISTED, NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));

    useCase.execute(videoId, userId);

    verify(notifications, never()).fanOutNewVideo(any(), any(), any());
  }

  @Test
  void republishDoesNotFanOut() {
    Video video = readyVideo(channelId);
    video.publish(NOW.minusSeconds(3600));
    when(videos.findById(videoId)).thenReturn(Optional.of(video));

    useCase.execute(videoId, userId);

    verify(notifications, never()).fanOutNewVideo(any(), any(), any());
  }

  @Test
  void rejectsVideoNotReady() {
    Video draft = Video.initiate(videoId, channelId, "T", "slug123", "videos/slug123", NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(VideoStatusConflictException.class);
  }

  @Test
  void republishKeepsOriginalPublicationInstant() {
    Video video = readyVideo(channelId);
    Instant earlier = NOW.minusSeconds(3600);
    video.publish(earlier);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));

    VideoInfoView view = useCase.execute(videoId, userId);

    assertThat(view.publishedAt()).isEqualTo(earlier);
  }

  @Test
  void rejectsNonOwner() {
    when(videos.findById(videoId)).thenReturn(Optional.of(readyVideo(UUID.randomUUID())));

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(ForbiddenVideoAccessException.class);
  }
}
