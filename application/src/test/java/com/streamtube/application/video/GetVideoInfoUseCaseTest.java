package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.social.VideoReactionRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.Visibility;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Covers the visibility matrix applied by {@link VideoViewAccess} on the read path. */
class GetVideoInfoUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private GetVideoInfoUseCase useCase;
  private UUID ownerUserId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    StoragePort storage = Mockito.mock(StoragePort.class);
    useCase =
        new GetVideoInfoUseCase(
            videos,
            storage,
            new VideoViewAccess(channels),
            Mockito.mock(VideoReactionRepository.class));

    ownerUserId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(ownerUserId))
        .thenReturn(
            Optional.of(Channel.createForUser(channelId, ownerUserId, "name", "nick", NOW)));
  }

  private Video draft() {
    Video v = Video.initiate(UUID.randomUUID(), channelId, "T", "slug123", "videos/slug123", NOW);
    when(videos.findBySlug("slug123")).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  void draftIs404ForAnonymousViewers() {
    draft();
    assertThatThrownBy(() -> useCase.execute("slug123", null))
        .isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void draftIs404ForOtherUsers() {
    draft();
    UUID otherUserId = UUID.randomUUID();
    when(channels.findByUserId(otherUserId))
        .thenReturn(
            Optional.of(
                Channel.createForUser(UUID.randomUUID(), otherUserId, "other", "other", NOW)));

    assertThatThrownBy(() -> useCase.execute("slug123", otherUserId))
        .isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void draftIsVisibleToItsOwner() {
    draft();
    VideoInfoView view = useCase.execute("slug123", ownerUserId);
    assertThat(view.slug()).isEqualTo("slug123");
  }

  @Test
  void publishedUnlistedVideoIsOpenToAnonymousViewers() {
    Video v = draft();
    v.markReady(12.5, null, "{}", NOW);
    v.changeVisibility(Visibility.UNLISTED, NOW);
    v.publish(NOW);

    VideoInfoView view = useCase.execute("slug123", null);

    assertThat(view.visibility()).isEqualTo("UNLISTED");
    assertThat(view.publishedAt()).isEqualTo(NOW);
  }
}
