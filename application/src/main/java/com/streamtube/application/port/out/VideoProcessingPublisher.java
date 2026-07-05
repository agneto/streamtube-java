package com.streamtube.application.port.out;

import java.util.UUID;

/**
 * Output port that publishes a video-processing job to the queue.
 *
 * <p>Implementations defer publishing until the calling transaction commits, so consumers never
 * see a job whose state change was not (or will not be) committed.
 */
public interface VideoProcessingPublisher {

  void publish(UUID videoId);
}
