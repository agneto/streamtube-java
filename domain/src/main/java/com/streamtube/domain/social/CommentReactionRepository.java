package com.streamtube.domain.social;

import java.util.Optional;
import java.util.UUID;

/** Output port for comment reactions — same contract as {@link VideoReactionRepository}. */
public interface CommentReactionRepository {

  Optional<ReactionType> find(UUID userId, UUID commentId);

  /** Sets or switches the reaction; a switch adjusts both counters. Idempotent per type. */
  void set(UUID userId, UUID commentId, ReactionType type);

  /** Removes the reaction and decrements its counter. Idempotent. */
  void remove(UUID userId, UUID commentId);
}
