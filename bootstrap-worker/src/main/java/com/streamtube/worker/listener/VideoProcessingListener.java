package com.streamtube.worker.listener;

import com.streamtube.application.video.ProcessVideoUseCase;
import com.streamtube.infrastructure.messaging.VideoProcessingMessage;
import com.streamtube.infrastructure.messaging.VideoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes processing jobs. On repeated failure the listener container retries and then routes the
 * message to the DLQ (queue declared with a dead-letter exchange); the DLQ listener marks the video
 * as ERROR.
 */
@Component
public class VideoProcessingListener {

  private static final Logger log = LoggerFactory.getLogger(VideoProcessingListener.class);

  private final ProcessVideoUseCase processVideo;

  public VideoProcessingListener(ProcessVideoUseCase processVideo) {
    this.processVideo = processVideo;
  }

  @RabbitListener(queues = VideoQueue.QUEUE)
  public void onMessage(VideoProcessingMessage message) {
    log.info("Processing video {}", message.videoId());
    processVideo.execute(message.videoId());
    log.info("Video {} ready", message.videoId());
  }

  @RabbitListener(queues = VideoQueue.DLQ)
  public void onDeadLetter(VideoProcessingMessage message) {
    log.error("Video {} failed processing after retries; marking ERROR", message.videoId());
    try {
      processVideo.markFailed(message.videoId(), "Processing failed after retries");
    } catch (Exception e) {
      // The DLQ has no dead-letter of its own: throwing here would drop the message and leave
      // the video stuck in QUEUED/PROCESSING forever with no trace. Log loudly instead —
      // this line is the alert signal for manual reconciliation.
      log.error(
          "Failed to mark video {} as ERROR; it may be stuck in an intermediate status",
          message.videoId(),
          e);
    }
  }
}
