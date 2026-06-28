package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
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
import org.mockito.Mockito;

class RenameVideoUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private RenameVideoUseCase useCase;

  private final UUID videoId = UUID.randomUUID();
  private final UUID ownerUserId = UUID.randomUUID();
  private final UUID channelId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    useCase = new RenameVideoUseCase(videos, channels, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    when(videos.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Video video() {
    return new Video(
        videoId, channelId, "Antigo", "slug123", VideoStatus.READY, "videos/slug123", null, 10.0,
        "{}", null, NOW, NOW);
  }

  private Channel ownerChannel() {
    return new Channel(channelId, ownerUserId, "Canal", "canal-x", null, NOW, NOW);
  }

  @Test
  void renamesWhenOwner() {
    when(videos.findById(videoId)).thenReturn(Optional.of(video()));
    when(channels.findByUserId(ownerUserId)).thenReturn(Optional.of(ownerChannel()));

    VideoInfoView view = useCase.execute(videoId, ownerUserId, "Novo título");

    assertThat(view.title()).isEqualTo("Novo título");
  }

  @Test
  void throwsWhenVideoMissing() {
    when(videos.findById(videoId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId, "x"))
        .isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void throwsWhenNotOwner() {
    UUID otherUser = UUID.randomUUID();
    when(videos.findById(videoId)).thenReturn(Optional.of(video()));
    when(channels.findByUserId(otherUser))
        .thenReturn(Optional.of(new Channel(UUID.randomUUID(), otherUser, "C", "c", null, NOW, NOW)));

    assertThatThrownBy(() -> useCase.execute(videoId, otherUser, "x"))
        .isInstanceOf(ForbiddenVideoAccessException.class);
  }
}
