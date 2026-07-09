package com.streamtube.application.social;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.domain.shared.SocialExceptions.CommentNotFoundException;
import com.streamtube.domain.shared.SocialExceptions.ForbiddenCommentAccessException;
import com.streamtube.domain.social.Comment;
import com.streamtube.domain.social.CommentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeleteCommentUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private CommentRepository comments;
  private DeleteCommentUseCase useCase;
  private UUID authorId;
  private UUID commentId;
  private Comment comment;

  @BeforeEach
  void setUp() {
    comments = Mockito.mock(CommentRepository.class);
    useCase = new DeleteCommentUseCase(comments);
    authorId = UUID.randomUUID();
    commentId = UUID.randomUUID();
    comment = Comment.create(commentId, UUID.randomUUID(), authorId, null, "meu", NOW);
    when(comments.findById(commentId)).thenReturn(Optional.of(comment));
  }

  @Test
  void authorDeletesOwnComment() {
    useCase.execute(commentId, authorId);
    verify(comments).delete(comment);
  }

  @Test
  void nonAuthorIsForbidden() {
    assertThatThrownBy(() -> useCase.execute(commentId, UUID.randomUUID()))
        .isInstanceOf(ForbiddenCommentAccessException.class);
    verify(comments, never()).delete(any());
  }

  @Test
  void unknownCommentIs404() {
    when(comments.findById(commentId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(commentId, authorId))
        .isInstanceOf(CommentNotFoundException.class);
  }
}
