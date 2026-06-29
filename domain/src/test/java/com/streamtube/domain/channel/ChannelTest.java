package com.streamtube.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.domain.shared.ChannelExceptions.InvalidChannelDescriptionException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private Channel newChannel() {
    return new Channel(
        UUID.randomUUID(), UUID.randomUUID(), "Original", "nickname", "descrição antiga", NOW, NOW);
  }

  @Test
  void updateDescription_storesNewDescriptionAndUpdatesTimestamp() {
    Channel channel = newChannel();
    Instant later = NOW.plusSeconds(60);

    channel.updateDescription("Nova descrição", later);

    assertThat(channel.description()).isEqualTo("Nova descrição");
    assertThat(channel.updatedAt()).isEqualTo(later);
  }

  @Test
  void updateDescription_acceptsNullToClearTheDescription() {
    Channel channel = newChannel();
    Instant later = NOW.plusSeconds(60);

    channel.updateDescription(null, later);

    assertThat(channel.description()).isNull();
    assertThat(channel.updatedAt()).isEqualTo(later);
  }

  @Test
  void updateDescription_acceptsBlankDescription() {
    Channel channel = newChannel();
    Instant later = NOW.plusSeconds(60);

    channel.updateDescription("", later);

    assertThat(channel.description()).isEmpty();
    assertThat(channel.updatedAt()).isEqualTo(later);
  }

  @Test
  void updateDescription_rejectsDescriptionOverMaxLength() {
    Channel channel = newChannel();
    String tooLong = "a".repeat(5001);

    assertThatThrownBy(() -> channel.updateDescription(tooLong, NOW.plusSeconds(60)))
        .isInstanceOf(InvalidChannelDescriptionException.class)
        .hasMessage("Channel description must be at most 5000 characters");
  }
}
