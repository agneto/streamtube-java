package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.video.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public channel page listing: published + PUBLIC videos only (UNLISTED stays reachable by slug
 * but never listed), most recently published first.
 */
@Service
public class ListChannelVideosUseCase {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;

  public ListChannelVideosUseCase(
      VideoRepository videoRepository, ChannelRepository channelRepository, StoragePort storage) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public PageResult<VideoSummaryView> execute(String nickname, int page, int size) {
    Channel channel =
        channelRepository.findByNickname(nickname).orElseThrow(ChannelNotFoundException::new);
    return videoRepository
        .findPublishedPublicPageByChannelId(
            channel.id(), PageRequests.page(page), PageRequests.size(size))
        .map(
            v ->
                VideoSummaryView.from(
                    v, v.thumbnailKey() == null ? null : storage.presignStream(v.thumbnailKey())));
  }
}
