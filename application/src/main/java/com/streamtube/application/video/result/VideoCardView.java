package com.streamtube.application.video.result;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.video.Video;
import java.time.Instant;
import java.util.UUID;

/**
 * Home-grid / search card: everything the reference grid shows (thumbnail, title, channel, views,
 * publication time). Unlike {@link VideoSummaryView} (channel-page listings, where the channel is
 * the page itself), the card embeds the channel identity.
 */
public record VideoCardView(
    UUID id,
    String slug,
    String title,
    String thumbnailUrl,
    Double durationSeconds,
    long views,
    Instant publishedAt,
    UUID categoryId,
    ChannelRef channel) {

  public record ChannelRef(UUID id, String name, String nickname) {}

  public static VideoCardView from(Video video, String thumbnailUrl, Channel channel) {
    return new VideoCardView(
        video.id(),
        video.slug(),
        video.title(),
        thumbnailUrl,
        video.durationSeconds(),
        video.viewsCount(),
        video.publishedAt(),
        video.categoryId(),
        channel == null ? null : new ChannelRef(channel.id(), channel.name(), channel.nickname()));
  }
}
