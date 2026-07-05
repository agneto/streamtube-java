package com.streamtube.infrastructure.messaging;

import com.streamtube.application.port.out.VideoProcessingPublisher;
import com.streamtube.infrastructure.transaction.AfterCommitExecutor;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes video-processing jobs to RabbitMQ, deferred until the surrounding transaction commits:
 * publishing inside the transaction would let the worker consume the job before (or without) the
 * QUEUED status ever being committed.
 *
 * <p>A publish failure after commit propagates (the request fails loudly) but the state change is
 * already durable; recovering that gap fully requires a transactional outbox.
 */
@Component
public class RabbitVideoProcessingPublisher implements VideoProcessingPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final AfterCommitExecutor afterCommit;

  public RabbitVideoProcessingPublisher(
      RabbitTemplate rabbitTemplate, AfterCommitExecutor afterCommit) {
    this.rabbitTemplate = rabbitTemplate;
    this.afterCommit = afterCommit;
  }

  @Override
  public void publish(UUID videoId) {
    afterCommit.run(
        () ->
            rabbitTemplate.convertAndSend(
                VideoQueue.EXCHANGE, VideoQueue.ROUTING_KEY, new VideoProcessingMessage(videoId)));
  }
}
