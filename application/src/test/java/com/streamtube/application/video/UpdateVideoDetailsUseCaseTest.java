package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.UpdateVideoDetailsUseCase.Command;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.category.CategoryRepository;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.InvalidCategoryException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.Visibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UpdateVideoDetailsUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private ChannelRepository channels;
  private CategoryRepository categories;
  private UpdateVideoDetailsUseCase useCase;
  private UUID videoId;
  private UUID userId;
  private UUID channelId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    categories = Mockito.mock(CategoryRepository.class);
    StoragePort storage = Mockito.mock(StoragePort.class);
    useCase =
        new UpdateVideoDetailsUseCase(
            videos, channels, categories, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    videoId = UUID.randomUUID();
    userId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(Optional.of(Channel.createForUser(channelId, userId, "name", "nick", NOW)));
    when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Video video(UUID owningChannel) {
    return Video.initiate(videoId, owningChannel, "Original", "slug123", "videos/slug123", NOW);
  }

  @Test
  void updatesOnlyProvidedFields() {
    Video video = video(channelId);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));

    VideoInfoView view =
        useCase.execute(
            videoId, userId, new Command(null, "Nova descrição", null, Visibility.UNLISTED));

    assertThat(view.title()).isEqualTo("Original"); // untouched
    assertThat(view.description()).isEqualTo("Nova descrição");
    assertThat(view.categoryId()).isNull(); // untouched
    assertThat(view.visibility()).isEqualTo("UNLISTED");
  }

  @Test
  void validatesCategoryExistence() {
    Video video = video(channelId);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));
    UUID categoryId = UUID.randomUUID();

    when(categories.existsById(categoryId)).thenReturn(false);
    assertThatThrownBy(
            () -> useCase.execute(videoId, userId, new Command(null, null, categoryId, null)))
        .isInstanceOf(InvalidCategoryException.class);

    when(categories.existsById(categoryId)).thenReturn(true);
    VideoInfoView view = useCase.execute(videoId, userId, new Command(null, null, categoryId, null));
    assertThat(view.categoryId()).isEqualTo(categoryId);
  }

  @Test
  void rejectsNonOwner() {
    when(videos.findById(videoId)).thenReturn(Optional.of(video(UUID.randomUUID())));

    assertThatThrownBy(() -> useCase.execute(videoId, userId, new Command("Novo", null, null, null)))
        .isInstanceOf(ForbiddenVideoAccessException.class);
  }

  @Test
  void rejectsUnknownVideo() {
    when(videos.findById(videoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(videoId, userId, new Command("Novo", null, null, null)))
        .isInstanceOf(VideoNotFoundException.class);
  }
}
