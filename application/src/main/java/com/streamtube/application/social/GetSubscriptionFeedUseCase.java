package com.streamtube.application.social;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feed of the subscribed-channels area: their latest published + PUBLIC videos. */
@Service
public class GetSubscriptionFeedUseCase {

  private final VideoRepository videoRepository;
  private final StoragePort storage;

  public GetSubscriptionFeedUseCase(VideoRepository videoRepository, StoragePort storage) {
    this.videoRepository = videoRepository;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public PageResult<VideoSummaryView> execute(UUID userId, int page, int size) {
    return videoRepository
        .findSubscriptionFeed(
            userId, SocialPageRequests.page(page), SocialPageRequests.size(size))
        .map(
            v ->
                VideoSummaryView.from(
                    v, v.thumbnailKey() == null ? null : storage.presignStream(v.thumbnailKey())));
  }
}
