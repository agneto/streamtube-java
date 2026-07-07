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

  public static final class InvalidChannelNameException extends DomainException {
    public InvalidChannelNameException() {
      super(
          "INVALID_CHANNEL_NAME",
          "Channel name must be between 1 and 50 characters",
          DomainErrorType.VALIDATION);
    }
  }

  public static final class InvalidNicknameException extends DomainException {
    public InvalidNicknameException() {
      super(
          "INVALID_NICKNAME",
          "Nickname must be 3-50 characters of letters, digits, '-' or '_'",
          DomainErrorType.VALIDATION);
    }
  }

  public static final class NicknameAlreadyTakenException extends DomainException {
    public NicknameAlreadyTakenException() {
      super("NICKNAME_ALREADY_TAKEN", "Nickname is already taken", DomainErrorType.CONFLICT);
    }
  }
}
