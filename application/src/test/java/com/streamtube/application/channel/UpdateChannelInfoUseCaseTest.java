package com.streamtube.application.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.streamtube.application.channel.UpdateChannelInfoUseCase.Command;
import com.streamtube.application.channel.result.ChannelInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.ChannelExceptions.InvalidNicknameException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UpdateChannelInfoUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private ChannelRepository channels;
  private UpdateChannelInfoUseCase useCase;
  private UUID userId;

  @BeforeEach
  void setUp() {
    channels = Mockito.mock(ChannelRepository.class);
    useCase = new UpdateChannelInfoUseCase(channels, Clock.fixed(NOW, ZoneOffset.UTC));
    userId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(
            Optional.of(
                new Channel(
                    UUID.randomUUID(), userId, "Original", "nick_original", "desc antiga", NOW,
                    NOW)));
    when(channels.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void updatesOnlyProvidedFields() {
    ChannelInfoView view = useCase.execute(userId, new Command(null, "novo-nick", null));

    assertThat(view.name()).isEqualTo("Original"); // untouched
    assertThat(view.nickname()).isEqualTo("novo-nick");
    assertThat(view.description()).isEqualTo("desc antiga"); // untouched
  }

  @Test
  void blankDescriptionClearsIt() {
    ChannelInfoView view = useCase.execute(userId, new Command(null, null, "  "));

    assertThat(view.description()).isNull();
  }

  @Test
  void propagatesDomainValidation() {
    assertThatThrownBy(() -> useCase.execute(userId, new Command(null, "a b", null)))
        .isInstanceOf(InvalidNicknameException.class);
  }

  @Test
  void rejectsUserWithoutChannel() {
    when(channels.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId, new Command("Nome", null, null)))
        .isInstanceOf(ChannelNotFoundException.class);
  }
}
