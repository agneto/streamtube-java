package com.streamtube.domain.shared;

/** Domain exceptions for the video context. */
public final class VideoExceptions {

  private VideoExceptions() {}

  public static final class VideoNotFoundException extends DomainException {
    public VideoNotFoundException() {
      super("VIDEO_NOT_FOUND", "Video not found");
    }
  }

  public static final class ForbiddenVideoAccessException extends DomainException {
    public ForbiddenVideoAccessException() {
      super("FORBIDDEN_VIDEO_ACCESS", "You do not own this video");
    }
  }

  public static final class UploadNotCompletedException extends DomainException {
    public UploadNotCompletedException() {
      super("UPLOAD_NOT_COMPLETED", "Video file not found in storage; upload may not have completed");
    }
  }

  public static final class VideoStatusConflictException extends DomainException {
    public VideoStatusConflictException() {
      super("VIDEO_STATUS_CONFLICT", "Operation not allowed in the current video status");
    }
  }

  public static final class VideoNotReadyException extends DomainException {
    public VideoNotReadyException() {
      super("VIDEO_NOT_READY", "Video is not ready for playback");
    }
  }
}
