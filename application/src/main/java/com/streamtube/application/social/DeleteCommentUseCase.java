package com.streamtube.application.social;

import com.streamtube.domain.shared.SocialExceptions.CommentNotFoundException;
import com.streamtube.domain.shared.SocialExceptions.ForbiddenCommentAccessException;
import com.streamtube.domain.social.Comment;
import com.streamtube.domain.social.CommentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the caller's own comment. Deleting a top-level comment removes its replies too — the
 * repository keeps the video's comment counter exact in the same transaction.
 */
@Service
public class DeleteCommentUseCase {

  private final CommentRepository commentRepository;

  public DeleteCommentUseCase(CommentRepository commentRepository) {
    this.commentRepository = commentRepository;
  }

  @Transactional
  public void execute(UUID commentId, UUID userId) {
    Comment comment =
        commentRepository.findById(commentId).orElseThrow(CommentNotFoundException::new);
    if (!comment.isAuthoredBy(userId)) {
      throw new ForbiddenCommentAccessException();
    }
    commentRepository.delete(comment);
  }
}
