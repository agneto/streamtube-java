package com.streamtube.application.video;

import com.streamtube.application.port.out.VideoProcessingPublisher;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Shared tail of both upload confirmations (single PUT and multipart): QUEUED + persist + job.
 * The publisher only emits after the surrounding transaction commits (AfterCommitExecutor), so
 * the worker can never see the job before the status is visible.
 */
@Component
class QueueForProcessing {

  private final VideoRepository videoRepository;
  private final VideoProcessingPublisher publisher;

  QueueForProcessing(VideoRepository videoRepository, VideoProcessingPublisher publisher) {
    this.videoRepository = videoRepository;
    this.publisher = publisher;
  }

  void execute(Video video, Instant now) {
    video.markQueued(now);
    videoRepository.save(video);
    publisher.publish(video.id());
  }
}
