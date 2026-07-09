package com.streamtube.application.social;

import com.streamtube.domain.shared.SocialExceptions.CommentNotFoundException;
import com.streamtube.domain.social.CommentReactionRepository;
import com.streamtube.domain.social.CommentRepository;
import com.streamtube.domain.social.ReactionType;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets or switches the caller's reaction on a comment. Comments only exist on published videos
 * (publication is one-way), so no video gate is needed here.
 */
@Service
public class SetCommentReactionUseCase {

  private final CommentRepository comments;
  private final CommentReactionRepository reactions;

  public SetCommentReactionUseCase(
      CommentRepository comments, CommentReactionRepository reactions) {
    this.comments = comments;
    this.reactions = reactions;
  }

  @Transactional
  public void execute(UUID commentId, UUID userId, ReactionType type) {
    comments.findById(commentId).orElseThrow(CommentNotFoundException::new);
    reactions.set(userId, commentId, type);
  }
}
