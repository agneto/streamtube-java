package com.streamtube.application.social;

import com.streamtube.application.social.result.CommentView;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.shared.SocialExceptions.CommentNotFoundException;
import com.streamtube.domain.social.CommentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replies of a comment, oldest first (conversation order). Public read: comments only exist on
 * published videos, so no extra video gate is needed.
 */
@Service
public class ListRepliesUseCase {

  private final CommentRepository commentRepository;
  private final CommentAuthors authors;

  public ListRepliesUseCase(CommentRepository commentRepository, CommentAuthors authors) {
    this.commentRepository = commentRepository;
    this.authors = authors;
  }

  @Transactional(readOnly = true)
  public PageResult<CommentView> execute(UUID commentId, int page, int size) {
    commentRepository.findById(commentId).orElseThrow(CommentNotFoundException::new);
    return authors.toViews(
        commentRepository.findRepliesByParentId(
            commentId, SocialPageRequests.page(page), SocialPageRequests.size(size)));
  }
}
