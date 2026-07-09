package com.streamtube.infrastructure.social;

import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.social.Comment;
import com.streamtube.domain.social.CommentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Saving/deleting moves the denormalized counters in the same transaction (the caller's), by
 * exactly the rows that changed: replies are bulk-deleted first so their count is exact, never a
 * racy SELECT-then-cascade estimate.
 */
@Repository
public class CommentRepositoryAdapter implements CommentRepository {

  private final CommentJpaRepository jpa;

  public CommentRepositoryAdapter(CommentJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Comment save(Comment comment) {
    jpa.save(toEntity(comment));
    jpa.adjustVideoCommentsCount(comment.videoId(), 1);
    if (comment.isReply()) {
      jpa.adjustRepliesCount(comment.parentId(), 1);
    }
    return comment;
  }

  @Override
  public Optional<Comment> findById(UUID id) {
    return jpa.findById(id).map(CommentRepositoryAdapter::toDomain);
  }

  @Override
  public PageResult<Comment> findTopLevelByVideoId(UUID videoId, int page, int size) {
    return toPageResult(
        jpa.findByVideoIdAndParentIdIsNullOrderByCreatedAtDesc(videoId, PageRequest.of(page, size)),
        page,
        size);
  }

  @Override
  public PageResult<Comment> findRepliesByParentId(UUID parentId, int page, int size) {
    return toPageResult(
        jpa.findByParentIdOrderByCreatedAtAsc(parentId, PageRequest.of(page, size)), page, size);
  }

  @Override
  public void delete(Comment comment) {
    if (comment.isReply()) {
      jpa.deleteById(comment.id());
      jpa.adjustVideoCommentsCount(comment.videoId(), -1);
      jpa.adjustRepliesCount(comment.parentId(), -1);
      return;
    }
    int replies = jpa.deleteReplies(comment.id());
    jpa.deleteById(comment.id());
    jpa.adjustVideoCommentsCount(comment.videoId(), -(replies + 1L));
  }

  private static PageResult<Comment> toPageResult(Page<CommentEntity> result, int page, int size) {
    return new PageResult<>(
        result.getContent().stream().map(CommentRepositoryAdapter::toDomain).toList(),
        page,
        size,
        result.getTotalElements());
  }

  private static CommentEntity toEntity(Comment c) {
    return new CommentEntity(
        c.id(),
        c.videoId(),
        c.userId(),
        c.parentId(),
        c.content(),
        c.likesCount(),
        c.dislikesCount(),
        c.repliesCount(),
        c.createdAt());
  }

  private static Comment toDomain(CommentEntity e) {
    return new Comment(
        e.getId(),
        e.getVideoId(),
        e.getUserId(),
        e.getParentId(),
        e.getContent(),
        e.getLikesCount(),
        e.getDislikesCount(),
        e.getRepliesCount(),
        e.getCreatedAt());
  }
}
