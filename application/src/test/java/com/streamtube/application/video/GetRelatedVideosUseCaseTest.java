package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Suggestions: category query vs uncategorized fallback, limit clamp and the base read rule. */
class GetRelatedVideosUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private GetRelatedVideosUseCase useCase;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    ChannelRepository channels = Mockito.mock(ChannelRepository.class);
    StoragePort storage = Mockito.mock(StoragePort.class);
    useCase = new GetRelatedVideosUseCase(videos, storage, new VideoViewAccess(channels));
    channelId = UUID.randomUUID();
  }

  private Video published(String slug, UUID categoryId) {
    Video v = Video.initiate(UUID.randomUUID(), channelId, "T", slug, "videos/" + slug, NOW);
    v.markReady(12.5, null, "{}", NOW);
    if (categoryId != null) {
      v.categorize(categoryId, NOW);
    }
    v.publish(NOW);
    when(videos.findBySlug(slug)).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  void categorizedVideoUsesTheCategoryQuery() {
    UUID categoryId = UUID.randomUUID();
    Video base = published("base1234", categoryId);
    Video other = published("other123", categoryId);
    when(videos.findRelatedByCategory(categoryId, base.id(), 10)).thenReturn(List.of(other));

    List<VideoSummaryView> related = useCase.execute("base1234", null, 10);

    assertThat(related).extracting(VideoSummaryView::slug).containsExactly("other123");
    assertThat(related.getFirst().views()).isZero();
  }

  @Test
  void uncategorizedVideoFallsBackToLatestListed() {
    Video base = published("base1234", null);
    when(videos.findLatestListed(base.id(), 10)).thenReturn(List.of());

    useCase.execute("base1234", null, 10);

    verify(videos).findLatestListed(base.id(), 10);
  }

  @Test
  void limitIsClampedToTheMaximum() {
    UUID categoryId = UUID.randomUUID();
    Video base = published("base1234", categoryId);
    when(videos.findRelatedByCategory(categoryId, base.id(), 20)).thenReturn(List.of());

    useCase.execute("base1234", null, 50);

    verify(videos).findRelatedByCategory(categoryId, base.id(), 20);
  }

  @Test
  void nonPositiveLimitIsClampedToOne() {
    UUID categoryId = UUID.randomUUID();
    Video base = published("base1234", categoryId);
    when(videos.findRelatedByCategory(categoryId, base.id(), 1)).thenReturn(List.of());

    useCase.execute("base1234", null, 0);

    verify(videos).findRelatedByCategory(categoryId, base.id(), 1);
  }

  @Test
  void draftBaseVideoIs404ForAnonymousViewers() {
    Video draft =
        Video.initiate(UUID.randomUUID(), channelId, "T", "draft123", "videos/draft123", NOW);
    when(videos.findBySlug("draft123")).thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> useCase.execute("draft123", null, 10))
        .isInstanceOf(VideoNotFoundException.class);
  }
}
