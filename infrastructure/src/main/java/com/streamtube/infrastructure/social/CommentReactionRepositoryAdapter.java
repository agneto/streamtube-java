package com.streamtube.infrastructure.social;

import com.streamtube.domain.social.CommentReactionRepository;
import com.streamtube.domain.social.ReactionType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Same row-count-gated counter logic as {@link VideoReactionRepositoryAdapter}. */
@Repository
public class CommentReactionRepositoryAdapter implements CommentReactionRepository {

  private final CommentReactionJpaRepository jpa;

  public CommentReactionRepositoryAdapter(CommentReactionJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<ReactionType> find(UUID userId, UUID commentId) {
    return jpa.findType(userId, commentId).map(ReactionType::valueOf);
  }

  @Override
  public void set(UUID userId, UUID commentId, ReactionType type) {
    if (jpa.insertIgnore(userId, commentId, type.name()) == 1) {
      jpa.adjustCounters(
          commentId,
          type == ReactionType.LIKE ? 1 : 0,
          type == ReactionType.DISLIKE ? 1 : 0);
      return;
    }
    if (jpa.switchType(userId, commentId, type.name()) == 1) {
      jpa.adjustCounters(
          commentId,
          type == ReactionType.LIKE ? 1 : -1,
          type == ReactionType.DISLIKE ? 1 : -1);
    }
  }

  @Override
  public void remove(UUID userId, UUID commentId) {
    if (jpa.deleteByType(userId, commentId, ReactionType.LIKE.name()) == 1) {
      jpa.adjustCounters(commentId, -1, 0);
    } else if (jpa.deleteByType(userId, commentId, ReactionType.DISLIKE.name()) == 1) {
      jpa.adjustCounters(commentId, 0, -1);
    }
  }
}
