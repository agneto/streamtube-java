package com.streamtube.api.web;

import com.streamtube.api.web.dto.SocialDtos.ReactionRequest;
import com.streamtube.application.social.DeleteCommentUseCase;
import com.streamtube.application.social.ListRepliesUseCase;
import com.streamtube.application.social.RemoveCommentReactionUseCase;
import com.streamtube.application.social.SetCommentReactionUseCase;
import com.streamtube.api.web.dto.PageResponse;
import com.streamtube.api.web.dto.SocialDtos.CommentResponse;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comments")
@Tag(name = "comments", description = "Replies, comment reactions and comment deletion")
public class CommentsController {

  private final ListRepliesUseCase listReplies;
  private final DeleteCommentUseCase deleteComment;
  private final SetCommentReactionUseCase setReaction;
  private final RemoveCommentReactionUseCase removeReaction;

  public CommentsController(
      ListRepliesUseCase listReplies,
      DeleteCommentUseCase deleteComment,
      SetCommentReactionUseCase setReaction,
      RemoveCommentReactionUseCase removeReaction) {
    this.listReplies = listReplies;
    this.deleteComment = deleteComment;
    this.setReaction = setReaction;
    this.removeReaction = removeReaction;
  }

  @GetMapping("/{id}/replies")
  @Operation(summary = "Replies of a comment, oldest first (public)")
  public PageResponse<CommentResponse> replies(
      @PathVariable("id") UUID id,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(listReplies.execute(id, page, size), SocialResponses::toComment);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete own comment (cascades its replies)")
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
    deleteComment.execute(id, principal.id());
  }

  @PutMapping("/{id}/reaction")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Set/switch my reaction on a comment (LIKE | DISLIKE)")
  public void react(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ReactionRequest request) {
    setReaction.execute(id, principal.id(), request.type());
  }

  @DeleteMapping("/{id}/reaction")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove my reaction from a comment (idempotent)")
  public void unreact(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
    removeReaction.execute(id, principal.id());
  }
}
