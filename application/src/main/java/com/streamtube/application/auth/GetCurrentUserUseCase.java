package com.streamtube.application.auth;

import com.streamtube.application.auth.result.CurrentUserView;
import com.streamtube.application.auth.result.CurrentUserView.ChannelView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.AuthExceptions.UserNotFoundException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns the authenticated user's profile plus their channel summary. */
@Service
public class GetCurrentUserUseCase {

  private final UserRepository userRepository;
  private final ChannelRepository channelRepository;

  public GetCurrentUserUseCase(
      UserRepository userRepository, ChannelRepository channelRepository) {
    this.userRepository = userRepository;
    this.channelRepository = channelRepository;
  }

  @Transactional(readOnly = true)
  public CurrentUserView execute(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    ChannelView channelView =
        channelRepository
            .findByUserId(userId)
            .map(this::toChannelView)
            .orElse(null);
    return new CurrentUserView(user.id(), user.email(), user.isConfirmed(), channelView);
  }

  private ChannelView toChannelView(Channel channel) {
    return new ChannelView(channel.id(), channel.nickname(), channel.name());
  }
}
