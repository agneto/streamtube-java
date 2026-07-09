package com.streamtube.application.social;

import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.social.ReactionType;
import com.streamtube.domain.social.VideoReactionRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sets or switches the caller's reaction on a published video. Idempotent per type. */
@Service
public class SetVideoReactionUseCase {

  private final VideoRepository videoRepository;
  private final VideoReactionRepository reactions;
  private final InteractionAccess access;

  public SetVideoReactionUseCase(
      VideoRepository videoRepository, VideoReactionRepository reactions, InteractionAccess access) {
    this.videoRepository = videoRepository;
    this.reactions = reactions;
    this.access = access;
  }

  @Transactional
  public void execute(UUID videoId, UUID userId, ReactionType type) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);
    access.ensureInteractable(video, userId);
    reactions.set(userId, videoId, type);
  }
}
