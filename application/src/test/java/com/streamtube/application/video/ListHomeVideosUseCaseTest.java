package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoCardView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListHomeVideosUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private ListHomeVideosUseCase useCase;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    StoragePort storage = Mockito.mock(StoragePort.class);
    useCase = new ListHomeVideosUseCase(videos, new VideoCards(channels, storage));
  }

  @Test
  void passesTheOptionalCategoryThrough() {
    UUID categoryId = UUID.randomUUID();
    when(videos.findListedPage(categoryId, 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

    useCase.execute(categoryId, 0, 20);
    verify(videos).findListedPage(categoryId, 0, 20);

    when(videos.findListedPage(null, 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
    useCase.execute(null, 0, 20);
    verify(videos).findListedPage(null, 0, 20);
  }

  @Test
  void assemblesCardsWithOneBatchChannelLookup() {
    UUID channelId = UUID.randomUUID();
    Video a = published(channelId, "s1");
    Video b = published(channelId, "s2");
    when(videos.findListedPage(null, 0, 20)).thenReturn(new PageResult<>(List.of(a, b), 0, 20, 2));
    when(channels.findByIds(List.of(channelId)))
        .thenReturn(
            List.of(Channel.createForUser(channelId, UUID.randomUUID(), "Canal", "nick", NOW)));

    PageResult<VideoCardView> result = useCase.execute(null, 0, 20);

    assertThat(result.items())
        .extracting(v -> v.channel().nickname())
        .containsExactly("nick", "nick");
    // distinct channel ids: the lookup happened once, with one id, despite two videos
    verify(channels).findByIds(List.of(channelId));
  }

  private Video published(UUID channelId, String slug) {
    Video v = Video.initiate(UUID.randomUUID(), channelId, "T", slug, "videos/" + slug, NOW);
    v.markReady(10.0, null, "{}", NOW);
    v.publish(NOW);
    return v;
  }
}
