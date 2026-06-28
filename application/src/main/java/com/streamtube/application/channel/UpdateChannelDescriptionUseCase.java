package com.streamtube.application.channel;

import com.streamtube.application.channel.result.ChannelInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates the description of the logged-in user's channel.
 *
 * <p>Application rule (the flow): find the caller's channel and persist; the "what is a valid
 * description" rule lives in the domain entity ({@link Channel#updateDescription}).
 */
@Service
public class UpdateChannelDescriptionUseCase {

  private final ChannelRepository channelRepository;
  private final Clock clock;

  public UpdateChannelDescriptionUseCase(ChannelRepository channelRepository, Clock clock) {
    this.channelRepository = Objects.requireNonNull(channelRepository);
    this.clock = Objects.requireNonNull(clock);
  }

  @Transactional
  public ChannelInfoView execute(UUID userId, String newDescription) {
    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ChannelNotFoundException::new);

    channel.updateDescription(newDescription, clock.instant());
    Channel saved = channelRepository.save(channel);

    return new ChannelInfoView(
        saved.id(),
        saved.userId(),
        saved.name(),
        saved.nickname(),
        saved.description(),
        saved.createdAt(),
        saved.updatedAt());
  }
}
