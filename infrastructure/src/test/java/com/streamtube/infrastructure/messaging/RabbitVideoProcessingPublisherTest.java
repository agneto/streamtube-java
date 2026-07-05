package com.streamtube.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.streamtube.infrastructure.transaction.AfterCommitExecutor;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RabbitVideoProcessingPublisherTest {

  private RabbitTemplate rabbitTemplate;
  private RabbitVideoProcessingPublisher publisher;
  private final UUID videoId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    rabbitTemplate = Mockito.mock(RabbitTemplate.class);
    publisher = new RabbitVideoProcessingPublisher(rabbitTemplate, new AfterCommitExecutor());
  }

  @AfterEach
  void cleanUp() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void doesNotPublishBeforeCommit() {
    TransactionSynchronizationManager.initSynchronization();

    publisher.publish(videoId);

    verify(rabbitTemplate, never())
        .convertAndSend(anyString(), anyString(), any(VideoProcessingMessage.class));
  }

  @Test
  void publishesAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();

    publisher.publish(videoId);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    verify(rabbitTemplate)
        .convertAndSend(
            VideoQueue.EXCHANGE, VideoQueue.ROUTING_KEY, new VideoProcessingMessage(videoId));
  }

  @Test
  void doesNotPublishOnRollback() {
    TransactionSynchronizationManager.initSynchronization();

    publisher.publish(videoId);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    verify(rabbitTemplate, never())
        .convertAndSend(anyString(), anyString(), any(VideoProcessingMessage.class));
  }
}
