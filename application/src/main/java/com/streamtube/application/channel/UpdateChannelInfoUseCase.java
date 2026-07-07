package com.streamtube.application.channel;

import com.streamtube.application.channel.result.ChannelInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partial update of the logged-in user's channel (name, nickname, description). PATCH semantics: a
 * {@code null} command field is left untouched; a blank description clears it. Field invariants
 * live in the domain entity; nickname uniqueness is enforced by the repository (409 on race).
 */
@Service
public class UpdateChannelInfoUseCase {

  /** All fields optional: {@code null} means "do not change". */
  public record Command(String name, String nickname, String description) {}

  private final ChannelRepository channelRepository;
  private final Clock clock;

  public UpdateChannelInfoUseCase(ChannelRepository channelRepository, Clock clock) {
    this.channelRepository = channelRepository;
    this.clock = clock;
  }

  @Transactional
  public ChannelInfoView execute(UUID userId, Command command) {
    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ChannelNotFoundException::new);

    Instant now = clock.instant();
    if (command.name() != null) {
      channel.rename(command.name(), now);
    }
    if (command.nickname() != null) {
      channel.changeNickname(command.nickname(), now);
    }
    if (command.description() != null) {
      channel.updateDescription(command.description().isBlank() ? null : command.description(), now);
    }

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
