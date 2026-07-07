package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.InvalidUploadSizeException;
import com.streamtube.domain.shared.VideoExceptions.UnsupportedThumbnailTypeException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InitiateThumbnailUploadUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final long MAX_SIZE = 5 * 1024 * 1024;

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private InitiateThumbnailUploadUseCase useCase;
  private UUID videoId;
  private UUID userId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    useCase = new InitiateThumbnailUploadUseCase(videos, channels, storage, MAX_SIZE);

    videoId = UUID.randomUUID();
    userId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(Optional.of(Channel.createForUser(channelId, userId, "name", "nick", NOW)));
  }

  private Video video(UUID owningChannel) {
    return Video.initiate(videoId, owningChannel, "T", "slug123", "videos/slug123", NOW);
  }

  @Test
  void signsImageUploadForTheCustomKey() {
    when(videos.findById(videoId)).thenReturn(Optional.of(video(channelId)));
    when(storage.presignUpload("thumbnails/slug123-custom", 1024, "image/png"))
        .thenReturn("http://minio/signed");

    String url = useCase.execute(videoId, userId, 1024, "image/png");

    assertThat(url).isEqualTo("http://minio/signed");
  }

  @Test
  void rejectsOversizedOrNonPositiveSize() {
    when(videos.findById(videoId)).thenReturn(Optional.of(video(channelId)));

    assertThatThrownBy(() -> useCase.execute(videoId, userId, MAX_SIZE + 1, "image/png"))
        .isInstanceOf(InvalidUploadSizeException.class);
    assertThatThrownBy(() -> useCase.execute(videoId, userId, 0, "image/png"))
        .isInstanceOf(InvalidUploadSizeException.class);
  }

  @Test
  void rejectsNonImageContentType() {
    when(videos.findById(videoId)).thenReturn(Optional.of(video(channelId)));

    assertThatThrownBy(() -> useCase.execute(videoId, userId, 1024, "video/mp4"))
        .isInstanceOf(UnsupportedThumbnailTypeException.class);
    assertThatThrownBy(() -> useCase.execute(videoId, userId, 1024, null))
        .isInstanceOf(UnsupportedThumbnailTypeException.class);
  }

  @Test
  void rejectsNonOwner() {
    when(videos.findById(videoId)).thenReturn(Optional.of(video(UUID.randomUUID())));

    assertThatThrownBy(() -> useCase.execute(videoId, userId, 1024, "image/png"))
        .isInstanceOf(ForbiddenVideoAccessException.class);
  }
}
