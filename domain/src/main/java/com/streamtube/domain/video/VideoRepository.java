package com.streamtube.domain.video;

import com.streamtube.domain.shared.PageResult;
import java.util.List;
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

  /** Atomic {@code views_count + 1}; never load-modify-save (concurrent viewers lose updates). */
  void incrementViews(UUID id);

  /** Watch-page suggestions: same category, published + PUBLIC, excluding the video itself. */
  List<Video> findRelatedByCategory(UUID categoryId, UUID excludeId, int limit);

  /** Suggestions fallback (uncategorized base video): latest published + PUBLIC platform-wide. */
  List<Video> findLatestListed(UUID excludeId, int limit);
}
