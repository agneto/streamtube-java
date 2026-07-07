package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partial update of a video's editable details (title, description, category, visibility) by its
 * owner. PATCH semantics: a {@code null} command field is left untouched; a blank description
 * clears it. Field invariants live in the domain entity; this use case orchestrates authorization
 * and category referential validity.
 */
@Service
public class UpdateVideoDetailsUseCase {

  /** All fields optional: {@code null} means "do not change". */
  public record Command(String title, String description, UUID categoryId, Visibility visibility) {}

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final CategoryRepository categoryRepository;
  private final StoragePort storage;
  private final Clock clock;

  public UpdateVideoDetailsUseCase(
      VideoRepository videoRepository,
      ChannelRepository channelRepository,
      CategoryRepository categoryRepository,
      StoragePort storage,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.categoryRepository = categoryRepository;
    this.storage = storage;
    this.clock = clock;
  }

  @Transactional
  public VideoInfoView execute(UUID videoId, UUID userId, Command command) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);

    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ForbiddenVideoAccessException::new);
    if (!video.channelId().equals(channel.id())) {
      throw new ForbiddenVideoAccessException();
    }

    Instant now = clock.instant();
    if (command.title() != null) {
      video.rename(command.title(), now);
    }
    if (command.description() != null) {
      video.describe(command.description(), now);
    }
    if (command.categoryId() != null) {
      if (!categoryRepository.existsById(command.categoryId())) {
        throw new InvalidCategoryException();
      }
      video.categorize(command.categoryId(), now);
    }
    if (command.visibility() != null) {
      video.changeVisibility(command.visibility(), now);
    }

    Video saved = videoRepository.save(video);
    String thumbnailUrl =
        saved.thumbnailKey() == null ? null : storage.presignStream(saved.thumbnailKey());
    return VideoInfoView.from(saved, thumbnailUrl);
  }
}
