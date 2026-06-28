package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.VideoProcessingPublisher;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.UploadNotCompletedException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoStatusConflictException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.VideoStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirms an upload landed in storage, transitions the video to QUEUED, and enqueues processing. */
@Service
public class CompleteUploadUseCase {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;
  private final VideoProcessingPublisher publisher;
  private final Clock clock;

  public CompleteUploadUseCase(
      VideoRepository videoRepository,
      ChannelRepository channelRepository,
      StoragePort storage,
      VideoProcessingPublisher publisher,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.storage = storage;
    this.publisher = publisher;
    this.clock = clock;
  }

  @Transactional
  public void execute(UUID videoId, UUID userId) {
    Video video =
        videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);

    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ForbiddenVideoAccessException::new);
    if (!video.channelId().equals(channel.id())) {
      throw new ForbiddenVideoAccessException();
    }
    if (video.status() != VideoStatus.PENDING_UPLOAD) {
      throw new VideoStatusConflictException();
    }
    if (!storage.objectExists(video.storageKey())) {
      throw new UploadNotCompletedException();
    }

    video.markQueued(clock.instant());
    videoRepository.save(video);
    publisher.publish(video.id());
  }
}
