package com.streamtube.domain.social;

import com.streamtube.domain.shared.PageResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for comments. Saving/deleting keeps the denormalized counters in sync in the same
 * transaction: the video's comments_count (top-level + replies) and the parent's replies_count.
 */
public interface CommentRepository {

  Comment save(Comment comment);

  Optional<Comment> findById(UUID id);

  /** Top-level comments of a video, newest first. */
  PageResult<Comment> findTopLevelByVideoId(UUID videoId, int page, int size);

  /** Replies of a top-level comment, oldest first (conversation order). */
  PageResult<Comment> findRepliesByParentId(UUID parentId, int page, int size);

  /**
   * Deletes the comment and (for top-level comments) its replies, decrementing the video's
   * comments_count by everything removed.
   */
  void delete(Comment comment);
}
