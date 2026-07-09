package com.streamtube.domain.shared;

/** Domain exceptions for the social context (reactions, comments, subscriptions). */
public final class SocialExceptions {

  private SocialExceptions() {}

  public static final class CommentNotFoundException extends DomainException {
    public CommentNotFoundException() {
      super("COMMENT_NOT_FOUND", "Comment not found", DomainErrorType.NOT_FOUND);
    }
  }

  public static final class InvalidCommentContentException extends DomainException {
    public InvalidCommentContentException() {
      super(
          "INVALID_COMMENT_CONTENT",
          "Comment content must be between 1 and 2000 characters",
          DomainErrorType.VALIDATION);
    }
  }

  public static final class InvalidParentCommentException extends DomainException {
    public InvalidParentCommentException() {
      super(
          "INVALID_PARENT_COMMENT",
          "Replies must target a top-level comment of the same video",
          DomainErrorType.VALIDATION);
    }
  }

  public static final class ForbiddenCommentAccessException extends DomainException {
    public ForbiddenCommentAccessException() {
      super(
          "FORBIDDEN_COMMENT_ACCESS",
          "Only the comment author may do this",
          DomainErrorType.FORBIDDEN);
    }
  }

  public static final class SelfSubscriptionException extends DomainException {
    public SelfSubscriptionException() {
      super(
          "SELF_SUBSCRIPTION",
          "You cannot subscribe to your own channel",
          DomainErrorType.VALIDATION);
    }
  }
}
