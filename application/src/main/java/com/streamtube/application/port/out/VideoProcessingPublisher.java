package com.streamtube.application.port.out;

import java.util.UUID;

/** Output port that publishes a video-processing job to the queue. */
public interface VideoProcessingPublisher {

  void publish(UUID videoId);
}
