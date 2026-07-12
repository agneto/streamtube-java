package com.streamtube.worker.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.streamtube.application.video.ProcessVideoUseCase;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.infrastructure.messaging.VideoProcessingMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Deleted-video jobs are dropped (ack), never retried into the DLQ; real failures still throw. */
class VideoProcessingListenerTest {

  private final ProcessVideoUseCase processVideo = Mockito.mock(ProcessVideoUseCase.class);
  private final VideoProcessingListener listener = new VideoProcessingListener(processVideo);

  @Test
  void deletedVideoIsDroppedWithoutRetry() {
    UUID videoId = UUID.randomUUID();
    doThrow(new VideoNotFoundException()).when(processVideo).execute(videoId);

    assertThatCode(() -> listener.onMessage(new VideoProcessingMessage(videoId)))
        .doesNotThrowAnyException();
  }

  @Test
  void realFailuresStillPropagateToTheRetryMachinery() {
    UUID videoId = UUID.randomUUID();
    doThrow(new IllegalStateException("ffmpeg exploded")).when(processVideo).execute(videoId);

    assertThatThrownBy(() -> listener.onMessage(new VideoProcessingMessage(videoId)))
        .isInstanceOf(IllegalStateException.class);
  }
}
