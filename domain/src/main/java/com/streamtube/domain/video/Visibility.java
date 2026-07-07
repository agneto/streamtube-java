package com.streamtube.domain.video;

/**
 * Who can reach a published video. {@code PUBLIC} appears in listings; {@code UNLISTED} is
 * reachable by slug (link-only) but never listed. Drafts (unpublished) are owner-only regardless
 * of this value.
 */
public enum Visibility {
  PUBLIC,
  UNLISTED
}
