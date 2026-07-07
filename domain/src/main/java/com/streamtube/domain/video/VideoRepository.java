package com.streamtube.domain.video;

import com.streamtube.domain.shared.PageResult;
import java.util.Optional;
import java.util.UUID;

/** Output port for video persistence. */
public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findById(UUID id);

  Optional<Video> findBySlug(String slug);

  boolean existsBySlug(String slug);

  /** Owner management panel: every status/visibility, newest first. */
  PageResult<Video> findPageByChannelId(UUID channelId, int page, int size);

  /** Public channel page: published + PUBLIC only, most recently published first. */
  PageResult<Video> findPublishedPublicPageByChannelId(UUID channelId, int page, int size);
}
