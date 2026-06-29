package com.streamtube.application.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.streamtube.application.channel.result.ChannelInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UpdateChannelDescriptionUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private ChannelRepository channelRepository;
  private UpdateChannelDescriptionUseCase useCase;

  @BeforeEach
  void setUp() {
    channelRepository = Mockito.mock(ChannelRepository.class);
    useCase = new UpdateChannelDescriptionUseCase(channelRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    when(channelRepository.save(Mockito.any(Channel.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private Channel channelOf(UUID userId) {
    return new Channel(
        UUID.randomUUID(), userId, "Canal", "nickname", "descrição antiga",
        NOW.minusSeconds(3600), NOW.minusSeconds(3600));
  }

  @Test
  void updatesDescriptionOfTheCallersChannel() {
    UUID userId = UUID.randomUUID();
    when(channelRepository.findByUserId(userId)).thenReturn(Optional.of(channelOf(userId)));

    ChannelInfoView view = useCase.execute(userId, "Nova descrição");

    assertThat(view.description()).isEqualTo("Nova descrição");
    assertThat(view.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void throwsWhenCallerHasNoChannel() {
    UUID userId = UUID.randomUUID();
    when(channelRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId, "x"))
        .isInstanceOf(ChannelNotFoundException.class);
  }
}
