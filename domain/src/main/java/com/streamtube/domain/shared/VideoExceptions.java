package com.streamtube.domain.shared;

/** Domain exceptions for the video context. */
public final class VideoExceptions {

  private VideoExceptions() {}

  public static final class VideoNotFoundException extends DomainException {
    public VideoNotFoundException() {
      super("VIDEO_NOT_FOUND", "Video not found", DomainErrorType.NOT_FOUND);
    }
  }

  public static final class ForbiddenVideoAccessException extends DomainException {
    public ForbiddenVideoAccessException() {
      super("FORBIDDEN_VIDEO_ACCESS", "You do not own this video", DomainErrorType.FORBIDDEN);
    }
  }

  public static final class UploadNotCompletedException extends DomainException {
    public UploadNotCompletedException() {
      super(
          "UPLOAD_NOT_COMPLETED",
          "Video file not found in storage; upload may not have completed",
          DomainErrorType.CONFLICT);
    }
  }

  public static final class VideoStatusConflictException extends DomainException {
    public VideoStatusConflictException() {
      super(
          "VIDEO_STATUS_CONFLICT",
          "Operation not allowed in the current video status",
          DomainErrorType.UNPROCESSABLE);
    }
  }

  public static final class VideoNotReadyException extends DomainException {
    public VideoNotReadyException() {
      super("VIDEO_NOT_READY", "Video is not ready for playback", DomainErrorType.UNPROCESSABLE);
    }
  }

  public static final class InvalidVideoTitleException extends DomainException {
    public InvalidVideoTitleException() {
      super(
          "INVALID_VIDEO_TITLE",
          "Video title must be between 1 and 255 characters",
          DomainErrorType.VALIDATION);
    }
  }

  public static final class InvalidUploadSizeException extends DomainException {
    public InvalidUploadSizeException() {
      super(
          "INVALID_UPLOAD_SIZE",
          "Upload size must be positive and within the allowed limit",
          DomainErrorType.VALIDATION);
    }
  }

  public static final class UnsupportedVideoTypeException extends DomainException {
    public UnsupportedVideoTypeException() {
      super(
          "UNSUPPORTED_VIDEO_TYPE",
          "Only video content types are accepted",
          DomainErrorType.VALIDATION);
    }
  }
}
