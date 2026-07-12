package com.streamtube.application.video;

import com.streamtube.domain.video.Video;
import java.util.List;

/**
 * Every storage family a video may own. Shared by deletion and the stale-draft purge so the two
 * paths can never drift apart — a family missed here becomes a permanent orphan the sweeper
 * cannot see. Prefix matching is collision-safe because slugs are fixed-length (11 chars): no
 * slug is a prefix of another.
 */
final class VideoArtifacts {

  private VideoArtifacts() {}

  static List<String> prefixesOf(Video video) {
    return List.of(
        video.storageKey(), // videos/{slug} — the original (exact key, valid as prefix)
        "thumbnails/" + video.slug(), // covers both {slug}.jpg and {slug}-custom
        "hls/" + video.slug() + "/"); // the whole ladder
  }
}
