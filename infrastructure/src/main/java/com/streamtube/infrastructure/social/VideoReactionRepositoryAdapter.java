package com.streamtube.infrastructure.social;

import com.streamtube.domain.social.ReactionType;
import com.streamtube.domain.social.VideoReactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Every counter move is gated by the affected-row count of the statement that changed the
 * reaction, so the like/dislike counters shift by exactly what happened — set, switch and remove
 * are idempotent and race-safe (the (user, video) PK arbitrates concurrent requests).
 */
@Repository
public class VideoReactionRepositoryAdapter implements VideoReactionRepository {

  private final VideoReactionJpaRepository jpa;

  public VideoReactionRepositoryAdapter(VideoReactionJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<ReactionType> find(UUID userId, UUID videoId) {
    return jpa.findType(userId, videoId).map(ReactionType::valueOf);
  }

  @Override
  public void set(UUID userId, UUID videoId, ReactionType type) {
    if (jpa.insertIgnore(userId, videoId, type.name()) == 1) {
      jpa.adjustCounters(videoId, delta(type, ReactionType.LIKE), delta(type, ReactionType.DISLIKE));
      return;
    }
    // already reacted: switch adjusts BOTH counters, same type is a no-op (0 rows affected)
    if (jpa.switchType(userId, videoId, type.name()) == 1) {
      jpa.adjustCounters(
          videoId,
          type == ReactionType.LIKE ? 1 : -1,
          type == ReactionType.DISLIKE ? 1 : -1);
    }
  }

  @Override
  public void remove(UUID userId, UUID videoId) {
    if (jpa.deleteByType(userId, videoId, ReactionType.LIKE.name()) == 1) {
      jpa.adjustCounters(videoId, -1, 0);
    } else if (jpa.deleteByType(userId, videoId, ReactionType.DISLIKE.name()) == 1) {
      jpa.adjustCounters(videoId, 0, -1);
    }
  }

  private static long delta(ReactionType actual, ReactionType counted) {
    return actual == counted ? 1 : 0;
  }
}
