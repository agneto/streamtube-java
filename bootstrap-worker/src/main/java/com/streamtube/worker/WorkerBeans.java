package com.streamtube.worker;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.VideoAnalyzer;
import com.streamtube.application.port.out.VideoTranscoder;
import com.streamtube.application.video.ProcessVideoUseCase;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Worker-only beans (the processing use case depends on the worker's FFmpeg ports). */
@Configuration
public class WorkerBeans {

  @Bean
  public ProcessVideoUseCase processVideoUseCase(
      VideoRepository videoRepository,
      StoragePort storage,
      VideoAnalyzer analyzer,
      VideoTranscoder transcoder,
      Clock clock) {
    return new ProcessVideoUseCase(videoRepository, storage, analyzer, transcoder, clock);
  }
}
