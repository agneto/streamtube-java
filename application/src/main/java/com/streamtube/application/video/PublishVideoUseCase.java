package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.Visibility;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a draft video owned by the caller. The domain rule ({@link Video#publish}) requires
 * processing to have reached READY; republishing is a no-op, so the endpoint is idempotent.
 */
@Service
public class PublishVideoUseCase {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final NotificationRepository notifications;
  private final StoragePort storage;
  private final Clock clock;

  public PublishVideoUseCase(
      VideoRepository videoRepository,
      ChannelRepository channelRepository,
      NotificationRepository notifications,
      StoragePort storage,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.notifications = notifications;
    this.storage = storage;
    this.clock = clock;
  }

  @Transactional
  public VideoInfoView execute(UUID videoId, UUID userId) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);

    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ForbiddenVideoAccessException::new);
    if (!video.channelId().equals(channel.id())) {
      throw new ForbiddenVideoAccessException();
    }

    // Capture before publishing: publish() is a no-op once published, so this distinguishes the
    // first publish from a republish and gates the one-shot fan-out (ADV-03).
    boolean wasPublished = video.isPublished();
    video.publish(clock.instant());
    Video saved = videoRepository.save(video);

    // Fan out NEW_VIDEO only on the first publish AND only for PUBLIC videos — an UNLISTED video
    // must not notify subscribers of content they cannot list. One set-based INSERT..SELECT over
    // subscriptions, in this transaction (ADV-06).
    if (!wasPublished && saved.isPublished() && saved.visibility() == Visibility.PUBLIC) {
      notifications.fanOutNewVideo(channel.id(), saved.id(), clock.instant());
    }

    String thumbnailUrl =
        saved.thumbnailKey() == null ? null : storage.presignStream(saved.thumbnailKey());
    return VideoInfoView.from(saved, thumbnailUrl);
  }
}
