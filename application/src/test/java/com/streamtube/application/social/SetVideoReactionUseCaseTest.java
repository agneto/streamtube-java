package com.streamtube.application.social;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotPublishedException;
import com.streamtube.domain.social.ReactionType;
import com.streamtube.domain.social.VideoReactionRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Covers the published-only rule on the reaction write path. */
class SetVideoReactionUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private VideoReactionRepository reactions;
  private SetVideoReactionUseCase useCase;
  private UUID ownerUserId;
  private UUID channelId;
  private UUID videoId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    reactions = Mockito.mock(VideoReactionRepository.class);
    useCase = new SetVideoReactionUseCase(videos, reactions, new InteractionAccess(channels));

    ownerUserId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    videoId = UUID.randomUUID();
    when(channels.findByUserId(ownerUserId))
        .thenReturn(
            Optional.of(Channel.createForUser(channelId, ownerUserId, "name", "nick", NOW)));
  }

  private Video video(boolean published) {
    Video v = Video.initiate(videoId, channelId, "T", "slug123", "videos/slug123", NOW);
    v.markReady(12.5, null, "{}", NOW);
    if (published) {
      v.publish(NOW);
    }
    when(videos.findById(videoId)).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  void reactsToPublishedVideoThroughThePort() {
    video(true);
    useCase.execute(videoId, ownerUserId, ReactionType.LIKE);
    verify(reactions).set(ownerUserId, videoId, ReactionType.LIKE);
  }

  @Test
  void ownDraftIsConflictForTheOwner() {
    video(false);
    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId, ReactionType.LIKE))
        .isInstanceOf(VideoNotPublishedException.class);
    verify(reactions, never()).set(any(), any(), any());
  }

  @Test
  void othersDraftIs404() {
    video(false);
    UUID otherUserId = UUID.randomUUID();
    when(channels.findByUserId(otherUserId))
        .thenReturn(
            Optional.of(
                Channel.createForUser(UUID.randomUUID(), otherUserId, "other", "other", NOW)));

    assertThatThrownBy(() -> useCase.execute(videoId, otherUserId, ReactionType.DISLIKE))
        .isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void unknownVideoIs404() {
    when(videos.findById(videoId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId, ReactionType.LIKE))
        .isInstanceOf(VideoNotFoundException.class);
  }
}
