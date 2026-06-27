package com.streamtube.infrastructure.messaging;

import com.streamtube.application.port.out.VideoProcessingPublisher;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publishes video-processing jobs to RabbitMQ. */
@Component
public class RabbitVideoProcessingPublisher implements VideoProcessingPublisher {

  private final RabbitTemplate rabbitTemplate;

  public RabbitVideoProcessingPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void publish(UUID videoId) {
    rabbitTemplate.convertAndSend(
        VideoQueue.EXCHANGE, VideoQueue.ROUTING_KEY, new VideoProcessingMessage(videoId));
  }
}
