package com.streamtube.application.social.result;

import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.social.Comment;
import java.time.Instant;
import java.util.UUID;

/** Comment projection with the author's public identity (their channel, never the user id). */
public record CommentView(
    UUID id,
    UUID videoId,
    UUID parentId,
    String content,
    Author author,
    long likes,
    long dislikes,
    long repliesCount,
    Instant createdAt) {

  public record Author(UUID channelId, String name, String nickname) {}

  public static CommentView from(Comment comment, Channel authorChannel) {
    return new CommentView(
        comment.id(),
        comment.videoId(),
        comment.parentId(),
        comment.content(),
        authorChannel == null
            ? null
            : new Author(authorChannel.id(), authorChannel.name(), authorChannel.nickname()),
        comment.likesCount(),
        comment.dislikesCount(),
        comment.repliesCount(),
        comment.createdAt());
  }
}
