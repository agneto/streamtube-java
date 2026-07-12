package com.streamtube.domain.video;

import com.streamtube.domain.shared.PageResult;
import java.time.Instant;
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

  /** Subscribed-channels feed: published + PUBLIC videos of channels the user follows. */
  PageResult<Video> findSubscriptionFeed(UUID userId, int page, int size);

  /** Hard delete; comments and reactions go with the row (DB-level cascades). */
  void delete(Video video);

  /** Stale drafts (PENDING_UPLOAD older than {@code cutoff}), oldest first — sweeper input. */
  List<Video> findStalePendingUploads(Instant cutoff, int limit);

  /** Home grid: published + PUBLIC platform-wide, newest first; {@code categoryId} optional. */
  PageResult<Video> findListedPage(UUID categoryId, int page, int size);

  /** Search: published + PUBLIC whose title or owning channel's name contains {@code query}. */
  PageResult<Video> searchListed(String query, int page, int size);
}
