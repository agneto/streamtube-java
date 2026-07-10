package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Playlist rewriting (URI lines only), the 404 matrix and view counting on the master fetch. */
class HlsPlaylistUseCasesTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private GetHlsMasterUseCase master;
  private GetHlsPlaylistUseCase playlist;
  private UUID ownerUserId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    VideoViewAccess access = new VideoViewAccess(channels);
    master = new GetHlsMasterUseCase(videos, storage, access);
    playlist = new GetHlsPlaylistUseCase(videos, storage, access, 21600);

    ownerUserId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(ownerUserId))
        .thenReturn(
            Optional.of(Channel.createForUser(channelId, ownerUserId, "name", "nick", NOW)));
  }

  private Video videoWithHls(boolean published) {
    Video v = Video.initiate(UUID.randomUUID(), channelId, "T", "slug123", "videos/slug123", NOW);
    v.markReady(12.5, null, "{}", "hls/slug123/master.m3u8", NOW);
    if (published) {
      v.publish(NOW);
    }
    when(videos.findBySlug("slug123")).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  void masterRewritesOnlyUriLinesAndCountsTheView() {
    Video v = videoWithHls(true);
    when(storage.getObjectText("hls/slug123/master.m3u8"))
        .thenReturn("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1528000\n480p/playlist.m3u8\n");

    String content = master.execute("slug123", null);

    assertThat(content)
        .contains("#EXT-X-STREAM-INF:BANDWIDTH=1528000") // tag line untouched
        .contains("/api/v1/videos/slug123/hls/480p/playlist.m3u8");
    verify(videos).incrementViews(v.id());
  }

  @Test
  void masterOfOwnDraftDoesNotCount() {
    videoWithHls(false);
    when(storage.getObjectText("hls/slug123/master.m3u8")).thenReturn("#EXTM3U\n");

    master.execute("slug123", ownerUserId);

    verify(videos, never()).incrementViews(any());
  }

  @Test
  void videoWithoutLadderIs404() {
    Video v = Video.initiate(UUID.randomUUID(), channelId, "T", "slug123", "videos/slug123", NOW);
    v.markReady(12.5, null, "{}", NOW); // pre-Phase-09 semantics: no HLS
    v.publish(NOW);
    when(videos.findBySlug("slug123")).thenReturn(Optional.of(v));

    assertThatThrownBy(() -> master.execute("slug123", null))
        .isInstanceOf(VideoNotFoundException.class);
    assertThatThrownBy(() -> playlist.execute("slug123", "480p", null))
        .isInstanceOf(VideoNotFoundException.class);
    verify(videos, never()).incrementViews(any());
  }

  @Test
  void renditionPlaylistPresignsSegmentsWithTheLongTtl() {
    videoWithHls(true);
    when(storage.objectExists("hls/slug123/480p/playlist.m3u8")).thenReturn(true);
    when(storage.getObjectText("hls/slug123/480p/playlist.m3u8"))
        .thenReturn("#EXTM3U\n#EXTINF:6.0,\nseg-000.ts\n#EXT-X-ENDLIST\n");
    when(storage.presignStream("hls/slug123/480p/seg-000.ts", 21600))
        .thenReturn("http://minio/signed-seg-000");

    String content = playlist.execute("slug123", "480p", null);

    assertThat(content)
        .contains("#EXTINF:6.0,") // tag line untouched
        .contains("http://minio/signed-seg-000")
        .contains("#EXT-X-ENDLIST");
    verify(videos, never()).incrementViews(any()); // only the master counts
  }

  @Test
  void unknownRenditionAndBadNamesAre404() {
    videoWithHls(true);
    when(storage.objectExists("hls/slug123/144p/playlist.m3u8")).thenReturn(false);

    assertThatThrownBy(() -> playlist.execute("slug123", "144p", null))
        .isInstanceOf(VideoNotFoundException.class);
    assertThatThrownBy(() -> playlist.execute("slug123", "..%2Fmaster", null))
        .isInstanceOf(VideoNotFoundException.class); // pattern-gated, no path traversal
  }

  @Test
  void draftIs404ForAnonymousViewers() {
    videoWithHls(false);
    assertThatThrownBy(() -> master.execute("slug123", null))
        .isInstanceOf(VideoNotFoundException.class);
  }
}
