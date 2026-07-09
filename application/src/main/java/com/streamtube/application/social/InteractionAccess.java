package com.streamtube.application.social;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotPublishedException;
import com.streamtube.domain.video.Video;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Write rule for social interactions: only published videos accept reactions/comments. Drafts
 * answer 404 to non-owners (their existence never leaks, same as the read rule) and 409 to the
 * owner — the video exists for them, it just is not published yet.
 */
@Component
class InteractionAccess {

  private final ChannelRepository channelRepository;

  InteractionAccess(ChannelRepository channelRepository) {
    this.channelRepository = channelRepository;
  }

  void ensureInteractable(Video video, UUID userId) {
    if (video.isPublished()) {
      return;
    }
    UUID channelId = channelRepository.findByUserId(userId).map(Channel::id).orElse(null);
    if (video.isAccessibleBy(channelId)) {
      throw new VideoNotPublishedException();
    }
    throw new VideoNotFoundException();
  }
}
