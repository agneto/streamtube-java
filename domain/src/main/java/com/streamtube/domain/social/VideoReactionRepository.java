package com.streamtube.domain.social;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for video reactions. Implementations keep the video's like/dislike counters in sync
 * with atomic SQL in the same transaction as the reaction change — the unique (user, video) row is
 * the source of truth, so counters are always recomputable.
 */
public interface VideoReactionRepository {

  Optional<ReactionType> find(UUID userId, UUID videoId);

  /** Sets or switches the reaction; a switch adjusts both counters. Idempotent per type. */
  void set(UUID userId, UUID videoId, ReactionType type);

  /** Removes the reaction and decrements its counter. Idempotent. */
  void remove(UUID userId, UUID videoId);
}
