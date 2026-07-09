package com.streamtube.application.social;

import com.streamtube.domain.shared.SocialExceptions.CommentNotFoundException;
import com.streamtube.domain.social.CommentReactionRepository;
import com.streamtube.domain.social.CommentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes the caller's reaction from a comment. Idempotent. */
@Service
public class RemoveCommentReactionUseCase {

  private final CommentRepository comments;
  private final CommentReactionRepository reactions;

  public RemoveCommentReactionUseCase(
      CommentRepository comments, CommentReactionRepository reactions) {
    this.comments = comments;
    this.reactions = reactions;
  }

  @Transactional
  public void execute(UUID commentId, UUID userId) {
    comments.findById(commentId).orElseThrow(CommentNotFoundException::new);
    reactions.remove(userId, commentId);
  }
}
