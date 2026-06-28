package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renames a video the caller owns.
 *
 * <p>Application rule (the flow): load the video, authorize the owner, then delegate the actual
 * title invariant to the domain entity ({@link Video#rename}) and persist. The "what is a valid
 * title" rule lives in the domain; this use case only orchestrates.
 */
@Service
public class RenameVideoUseCase {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;
  private final Clock clock;

  public RenameVideoUseCase(
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
  public VideoInfoView execute(UUID videoId, UUID userId, String newTitle) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);

    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ForbiddenVideoAccessException::new);
    if (!video.channelId().equals(channel.id())) {
      throw new ForbiddenVideoAccessException();
    }

    video.rename(newTitle, clock.instant());
    Video saved = videoRepository.save(video);

    String thumbnailUrl =
        saved.thumbnailKey() == null ? null : storage.presignStream(saved.thumbnailKey());
    return new VideoInfoView(
        saved.id(),
        saved.slug(),
        saved.title(),
        saved.status().name(),
        thumbnailUrl,
        saved.durationSeconds(),
        saved.channelId(),
        saved.createdAt());
  }
}
