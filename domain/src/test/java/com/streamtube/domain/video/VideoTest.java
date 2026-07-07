package com.streamtube.domain.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.domain.shared.VideoExceptions.InvalidVideoDescriptionException;
import com.streamtube.domain.shared.VideoExceptions.InvalidVideoTitleException;
import com.streamtube.domain.shared.VideoExceptions.VideoStatusConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VideoTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private Video newVideo() {
    return Video.initiate(
        UUID.randomUUID(), UUID.randomUUID(), "Original", "slug123", "videos/slug123", NOW);
  }

  private Video readyVideo() {
    Video v = newVideo();
    v.markReady(12.5, "thumbnails/slug123.jpg", "{}", NOW);
    return v;
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

  @Test
  void initiateCreatesPublicDraft() {
    Video v = newVideo();
    assertThat(v.visibility()).isEqualTo(Visibility.PUBLIC);
    assertThat(v.publishedAt()).isNull();
    assertThat(v.isPublished()).isFalse();
  }

  @Test
  void describeStoresDescriptionAndBlankClearsIt() {
    Video v = newVideo();

    v.describe("Uma descrição", NOW.plusSeconds(60));
    assertThat(v.description()).isEqualTo("Uma descrição");

    v.describe("   ", NOW.plusSeconds(120));
    assertThat(v.description()).isNull();
  }

  @Test
  void describeRejectsDescriptionOverMaxLength() {
    Video v = newVideo();
    assertThatThrownBy(() -> v.describe("a".repeat(5001), NOW))
        .isInstanceOf(InvalidVideoDescriptionException.class);
  }

  @Test
  void publishRequiresReadyStatus() {
    Video draft = newVideo(); // PENDING_UPLOAD
    assertThatThrownBy(() -> draft.publish(NOW)).isInstanceOf(VideoStatusConflictException.class);
    assertThat(draft.publishedAt()).isNull();
  }

  @Test
  void publishSetsPublishedAtAndRepublishIsNoOp() {
    Video v = readyVideo();
    Instant first = NOW.plusSeconds(60);

    v.publish(first);
    assertThat(v.publishedAt()).isEqualTo(first);

    v.publish(NOW.plusSeconds(120)); // no-op: keeps the original publication instant
    assertThat(v.publishedAt()).isEqualTo(first);
  }

  @Test
  void changeThumbnailRequiresReadyStatus() {
    Video draft = newVideo();
    assertThatThrownBy(() -> draft.changeThumbnail("thumbnails/slug123-custom", NOW))
        .isInstanceOf(VideoStatusConflictException.class);

    Video ready = readyVideo();
    ready.changeThumbnail("thumbnails/slug123-custom", NOW.plusSeconds(60));
    assertThat(ready.thumbnailKey()).isEqualTo("thumbnails/slug123-custom");
  }

  @Test
  void draftIsAccessibleOnlyByOwner() {
    Video draft = newVideo();
    assertThat(draft.isAccessibleBy(draft.channelId())).isTrue();
    assertThat(draft.isAccessibleBy(UUID.randomUUID())).isFalse();
    assertThat(draft.isAccessibleBy(null)).isFalse();
  }

  @Test
  void publishedVideoIsAccessibleByAnyoneRegardlessOfVisibility() {
    Video v = readyVideo();
    v.changeVisibility(Visibility.UNLISTED, NOW);
    v.publish(NOW.plusSeconds(60));

    assertThat(v.isAccessibleBy(null)).isTrue();
    assertThat(v.isAccessibleBy(UUID.randomUUID())).isTrue();
  }

  @Test
  void onlyPublishedPublicVideosAreListedPublicly() {
    Video draft = newVideo();
    assertThat(draft.isListedPublicly()).isFalse(); // PUBLIC but not published

    Video unlisted = readyVideo();
    unlisted.changeVisibility(Visibility.UNLISTED, NOW);
    unlisted.publish(NOW.plusSeconds(60));
    assertThat(unlisted.isListedPublicly()).isFalse();

    Video published = readyVideo();
    published.publish(NOW.plusSeconds(60));
    assertThat(published.isListedPublicly()).isTrue();
  }
}
