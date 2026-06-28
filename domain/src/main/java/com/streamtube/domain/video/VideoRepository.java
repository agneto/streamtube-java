package com.streamtube.domain.video;

import java.util.Optional;
import java.util.UUID;

/** Output port for video persistence. */
public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findById(UUID id);

  Optional<Video> findBySlug(String slug);

  boolean existsBySlug(String slug);
}
