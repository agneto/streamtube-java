package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoCardView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.shared.VideoExceptions.InvalidSearchQueryException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Covers the query rule (trim + minimum length) and card assembly with the channel identity. */
class SearchVideosUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private SearchVideosUseCase useCase;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    StoragePort storage = Mockito.mock(StoragePort.class);
    useCase = new SearchVideosUseCase(videos, new VideoCards(channels, storage));
  }

  @Test
  void shortOrBlankQueriesAreRejected() {
    for (String q : new String[] {null, "", "a", "  a  ", "   "}) {
      assertThatThrownBy(() -> useCase.execute(q, 0, 20))
          .isInstanceOf(InvalidSearchQueryException.class);
    }
    verify(videos, never()).searchListed(any(), anyInt(), anyInt());
  }

  @Test
  void trimsTheQueryAndAssemblesCardsWithTheChannel() {
    UUID channelId = UUID.randomUUID();
    Video video = Video.initiate(UUID.randomUUID(), channelId, "Aula", "s1", "videos/s1", NOW);
    video.markReady(10.0, null, "{}", NOW);
    video.publish(NOW);
    when(videos.searchListed("aula", 0, 20))
        .thenReturn(new PageResult<>(List.of(video), 0, 20, 1));
    when(channels.findByIds(List.of(channelId)))
        .thenReturn(
            List.of(Channel.createForUser(channelId, UUID.randomUUID(), "Canal", "nick", NOW)));

    PageResult<VideoCardView> result = useCase.execute("  aula  ", 0, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().channel().nickname()).isEqualTo("nick");
    verify(videos).searchListed("aula", 0, 20);
  }
}
