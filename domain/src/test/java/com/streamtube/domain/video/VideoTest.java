package com.streamtube.domain.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.domain.shared.VideoExceptions.InvalidVideoTitleException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VideoTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private Video newVideo() {
    return Video.initiate(
        UUID.randomUUID(), UUID.randomUUID(), "Original", "slug123", "videos/slug123", NOW);
  }

  @Test
  void renameUpdatesTitleAndTimestamp() {
    Video v = newVideo();
    Instant later = NOW.plusSeconds(60);

    v.rename("  Novo Título  ", later);

    assertThat(v.title()).isEqualTo("Novo Título"); // trimmed
    assertThat(v.updatedAt()).isEqualTo(later);
  }

  @Test
  void renameRejectsBlankTitle() {
    Video v = newVideo();
    assertThatThrownBy(() -> v.rename("   ", NOW)).isInstanceOf(InvalidVideoTitleException.class);
  }

  @Test
  void renameRejectsTooLongTitle() {
    Video v = newVideo();
    String tooLong = "a".repeat(256);
    assertThatThrownBy(() -> v.rename(tooLong, NOW)).isInstanceOf(InvalidVideoTitleException.class);
  }
}
