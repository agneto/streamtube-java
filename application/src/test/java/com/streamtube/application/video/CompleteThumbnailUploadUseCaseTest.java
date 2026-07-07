package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.UploadNotCompletedException;
import com.streamtube.domain.shared.VideoExceptions.VideoStatusConflictException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CompleteThumbnailUploadUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private CompleteThumbnailUploadUseCase useCase;
  private UUID videoId;
  private UUID userId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    useCase =
        new CompleteThumbnailUploadUseCase(
            videos, channels, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    videoId = UUID.randomUUID();
    userId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(Optional.of(Channel.createForUser(channelId, userId, "name", "nick", NOW)));
    when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Video readyVideo() {
    Video v = Video.initiate(videoId, channelId, "T", "slug123", "videos/slug123", NOW);
    v.markReady(12.5, "thumbnails/slug123.jpg", "{}", NOW);
    return v;
  }

  @Test
  void swapsThumbnailToTheCustomKey() {
    Video video = readyVideo();
    when(videos.findById(videoId)).thenReturn(Optional.of(video));
    when(storage.objectExists("thumbnails/slug123-custom")).thenReturn(true);
    when(storage.presignStream("thumbnails/slug123-custom")).thenReturn("http://minio/custom");

    VideoInfoView view = useCase.execute(videoId, userId);

    assertThat(video.thumbnailKey()).isEqualTo("thumbnails/slug123-custom");
    assertThat(view.thumbnailUrl()).isEqualTo("http://minio/custom");
  }

  @Test
  void rejectsWhenObjectIsMissingFromStorage() {
    when(videos.findById(videoId)).thenReturn(Optional.of(readyVideo()));
    when(storage.objectExists("thumbnails/slug123-custom")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(UploadNotCompletedException.class);
  }

  @Test
  void rejectsVideoNotReady() {
    Video draft = Video.initiate(videoId, channelId, "T", "slug123", "videos/slug123", NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(draft));
    when(storage.objectExists("thumbnails/slug123-custom")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(VideoStatusConflictException.class);
  }
}
