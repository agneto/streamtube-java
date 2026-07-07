package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner management panel: every status/visibility of the caller's videos, newest first. */
@Service
public class ListMyVideosUseCase {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;

  public ListMyVideosUseCase(
      VideoRepository videoRepository, ChannelRepository channelRepository, StoragePort storage) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public PageResult<VideoSummaryView> execute(UUID userId, int page, int size) {
    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ChannelNotFoundException::new);
    return videoRepository
        .findPageByChannelId(channel.id(), PageRequests.page(page), PageRequests.size(size))
        .map(
            v ->
                VideoSummaryView.from(
                    v, v.thumbnailKey() == null ? null : storage.presignStream(v.thumbnailKey())));
  }
}
