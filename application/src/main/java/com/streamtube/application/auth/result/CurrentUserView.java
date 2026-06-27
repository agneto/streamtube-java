package com.streamtube.application.auth.result;

import java.util.UUID;

public record CurrentUserView(
    UUID id, String email, boolean confirmed, ChannelView channel) {

  public record ChannelView(UUID id, String nickname, String name) {}
}
