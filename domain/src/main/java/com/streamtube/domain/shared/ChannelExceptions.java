package com.streamtube.domain.shared;

public final class ChannelExceptions {

  private ChannelExceptions() {}

  public static final class ChannelNotFoundException extends DomainException {
    public ChannelNotFoundException() {
      super("CHANNEL_NOT_FOUND", "Channel not found", DomainErrorType.NOT_FOUND);
    }
  }

  public static final class InvalidChannelDescriptionException extends DomainException {
    public InvalidChannelDescriptionException() {
      super(
          "INVALID_CHANNEL_DESCRIPTION",
          "Channel description must be at most 5000 characters",
          DomainErrorType.VALIDATION);
    }
  }
}
