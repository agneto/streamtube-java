package com.streamtube.application.video;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Owner-only write rule shared by the upload endpoints: 404 unknown video, 403 not yours. */
@Component
class VideoOwnership {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;

  VideoOwnership(VideoRepository videoRepository, ChannelRepository channelRepository) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
  }

  Video requireOwned(UUID videoId, UUID userId) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);
    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ForbiddenVideoAccessException::new);
    if (!video.channelId().equals(channel.id())) {
      throw new ForbiddenVideoAccessException();
    }
    return video;
  }
}
