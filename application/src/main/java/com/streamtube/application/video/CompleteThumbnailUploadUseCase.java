package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.UploadNotCompletedException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms a custom thumbnail landed in storage and swaps the video's thumbnail key to it. The
 * domain rule ({@link Video#changeThumbnail}) requires the video to be READY.
 */
@Service
public class CompleteThumbnailUploadUseCase {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;
  private final Clock clock;

  public CompleteThumbnailUploadUseCase(
      VideoRepository videoRepository,
      ChannelRepository channelRepository,
      StoragePort storage,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.storage = storage;
    this.clock = clock;
  }

  @Transactional
  public VideoInfoView execute(UUID videoId, UUID userId) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);

    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ForbiddenVideoAccessException::new);
    if (!video.channelId().equals(channel.id())) {
      throw new ForbiddenVideoAccessException();
    }

    String key = InitiateThumbnailUploadUseCase.customThumbnailKey(video);
    if (!storage.objectExists(key)) {
      throw new UploadNotCompletedException();
    }

    video.changeThumbnail(key, clock.instant());
    Video saved = videoRepository.save(video);

    return VideoInfoView.from(saved, storage.presignStream(key));
  }
}
