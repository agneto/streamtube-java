package com.streamtube.application.video;

import com.streamtube.application.port.out.SlugGenerator;
import com.streamtube.domain.video.VideoRepository;
import org.springframework.stereotype.Component;

/** Allocates a slug not yet taken (shared by both upload initiations). */
@Component
class UniqueSlugs {

  private static final int MAX_ATTEMPTS = 5;

  private final SlugGenerator slugGenerator;
  private final VideoRepository videoRepository;

  UniqueSlugs(SlugGenerator slugGenerator, VideoRepository videoRepository) {
    this.slugGenerator = slugGenerator;
    this.videoRepository = videoRepository;
  }

  String next() {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      String slug = slugGenerator.generate();
      if (!videoRepository.existsBySlug(slug)) {
        return slug;
      }
    }
    throw new IllegalStateException("Could not generate a unique slug after retries");
  }
}
