package com.streamtube.worker;

import com.streamtube.application.port.out.StorageCleanupQueue;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.VideoAnalyzer;
import com.streamtube.application.port.out.VideoTranscoder;
import com.streamtube.application.video.ProcessStorageCleanupsUseCase;
import com.streamtube.application.video.ProcessVideoUseCase;
import com.streamtube.application.video.PurgeStaleUploadsUseCase;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Worker-only beans (processing and housekeeping depend on worker-side concerns). */
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

  @Bean
  public PurgeStaleUploadsUseCase purgeStaleUploadsUseCase(
      VideoRepository videoRepository,
      StoragePort storage,
      StorageCleanupQueue cleanups,
      Clock clock,
      @Value("${cleanup.stale-upload-days:7}") int staleUploadDays) {
    return new PurgeStaleUploadsUseCase(videoRepository, storage, cleanups, clock, staleUploadDays);
  }

  @Bean
  public ProcessStorageCleanupsUseCase processStorageCleanupsUseCase(
      StorageCleanupQueue cleanups, StoragePort storage) {
    return new ProcessStorageCleanupsUseCase(cleanups, storage);
  }
}
