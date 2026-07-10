package com.streamtube.api.web;

import com.streamtube.api.web.dto.VideoDtos.ChannelRefResponse;
import com.streamtube.api.web.dto.VideoDtos.VideoCardResponse;
import com.streamtube.application.video.result.VideoCardView;

/** Card view → response mapping shared by the home listing and the search endpoint. */
final class VideoCardResponses {

  private VideoCardResponses() {}

  static VideoCardResponse from(VideoCardView v) {
    return new VideoCardResponse(
        v.id(),
        v.slug(),
        v.title(),
        v.thumbnailUrl(),
        v.durationSeconds(),
        v.views(),
        v.publishedAt(),
        v.categoryId(),
        v.channel() == null
            ? null
            : new ChannelRefResponse(
                v.channel().id(), v.channel().name(), v.channel().nickname()));
  }
}
