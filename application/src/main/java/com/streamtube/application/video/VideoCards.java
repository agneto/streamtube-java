package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.VideoCardView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.video.Video;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Assembles grid cards: one batch channel lookup per page (no N+1), thumbnails presigned. */
@Component
class VideoCards {

  private final ChannelRepository channelRepository;
  private final StoragePort storage;

  VideoCards(ChannelRepository channelRepository, StoragePort storage) {
    this.channelRepository = channelRepository;
    this.storage = storage;
  }

  PageResult<VideoCardView> toCards(PageResult<Video> videos) {
    Map<UUID, Channel> byId =
        channelRepository
            .findByIds(videos.items().stream().map(Video::channelId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Channel::id, Function.identity()));
    return videos.map(
        v ->
            VideoCardView.from(
                v,
                v.thumbnailKey() == null ? null : storage.presignStream(v.thumbnailKey()),
                byId.get(v.channelId())));
  }
}
