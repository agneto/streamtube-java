package com.streamtube.application.video;

import com.streamtube.application.port.out.SlugGenerator;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.InitiateUploadResult;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft video and returns a presigned URL for the client to upload directly to storage. */
@Service
public class InitiateUploadUseCase {

  private static final int MAX_SLUG_ATTEMPTS = 5;

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;
  private final SlugGenerator slugGenerator;
  private final Clock clock;

  public InitiateUploadUseCase(
      VideoRepository videoRepository,
      ChannelRepository channelRepository,
      StoragePort storage,
      SlugGenerator slugGenerator,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.storage = storage;
    this.slugGenerator = slugGenerator;
    this.clock = clock;
  }

  @Transactional
  public InitiateUploadResult execute(UUID userId, String title) {
    Channel channel =
        channelRepository
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("User has no channel"));

    String slug = generateUniqueSlug();
    String storageKey = "videos/" + slug;
    Instant now = clock.instant();
    Video video =
        videoRepository.save(
            Video.initiate(UUID.randomUUID(), channel.id(), title, slug, storageKey, now));

    String uploadUrl = storage.presignUpload(storageKey);
    return new InitiateUploadResult(video.id(), video.slug(), uploadUrl);
  }

  private String generateUniqueSlug() {
    for (int attempt = 0; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
      String slug = slugGenerator.generate();
      if (!videoRepository.existsBySlug(slug)) {
        return slug;
      }
    }
    throw new IllegalStateException("Could not generate a unique slug after retries");
  }
}
