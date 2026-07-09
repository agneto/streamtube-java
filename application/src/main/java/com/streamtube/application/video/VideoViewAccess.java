package com.streamtube.application.video;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Read rule shared by info/stream/download and the social read paths: published videos (any
 * visibility) are open to everyone; drafts answer 404 to anyone but the owner, so their existence
 * never leaks.
 */
@Component
public class VideoViewAccess {

  private final ChannelRepository channelRepository;

  VideoViewAccess(ChannelRepository channelRepository) {
    this.channelRepository = channelRepository;
  }

  /** {@code viewerUserId} is null for anonymous requests. */
  public void ensureViewable(Video video, UUID viewerUserId) {
    if (video.isPublished()) {
      return;
    }
    UUID viewerChannelId =
        viewerUserId == null
            ? null
            : channelRepository.findByUserId(viewerUserId).map(Channel::id).orElse(null);
    if (!video.isAccessibleBy(viewerChannelId)) {
      throw new VideoNotFoundException();
    }
  }
}
