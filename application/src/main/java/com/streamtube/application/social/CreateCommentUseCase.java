package com.streamtube.application.social;

import com.streamtube.application.social.result.CommentView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.notification.Notification;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.SocialExceptions.InvalidParentCommentException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.social.Comment;
import com.streamtube.domain.social.CommentRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comments on a published video. Replies are single-level: the parent must be a top-level comment
 * of the same video — anything else (unknown parent, reply-to-reply, parent of another video) is
 * the same 400, so parent ids of other videos' comments are not probeable.
 */
@Service
public class CreateCommentUseCase {

  private final VideoRepository videoRepository;
  private final CommentRepository commentRepository;
  private final ChannelRepository channelRepository;
  private final NotificationRepository notifications;
  private final InteractionAccess access;
  private final Clock clock;

  public CreateCommentUseCase(
      VideoRepository videoRepository,
      CommentRepository commentRepository,
      ChannelRepository channelRepository,
      NotificationRepository notifications,
      InteractionAccess access,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.commentRepository = commentRepository;
    this.channelRepository = channelRepository;
    this.notifications = notifications;
    this.access = access;
    this.clock = clock;
  }

  @Transactional
  public CommentView execute(UUID videoId, UUID userId, String content, UUID parentId) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);
    access.ensureInteractable(video, userId);
    Comment parent = null;
    if (parentId != null) {
      parent = commentRepository.findById(parentId).orElseThrow(InvalidParentCommentException::new);
      if (parent.isReply() || !parent.videoId().equals(videoId)) {
        throw new InvalidParentCommentException();
      }
    }
    Comment comment =
        commentRepository.save(
            Comment.create(UUID.randomUUID(), videoId, userId, parentId, content, clock.instant()));
    Channel author =
        channelRepository.findByUserId(userId).orElseThrow(ChannelNotFoundException::new);
    notifyAffectedUser(video, parent, comment, author);
    return CommentView.from(comment, author);
  }

  /**
   * Drops a notification for the affected user, in the same transaction as the comment (ADV-01):
   * top-level → the video owner (VIDEO_COMMENT); reply → the parent's author (COMMENT_REPLY).
   * Self-interactions are suppressed (own video / own comment).
   */
  private void notifyAffectedUser(Video video, Comment parent, Comment comment, Channel author) {
    if (parent == null) {
      if (author.id().equals(video.channelId())) {
        return; // commenting on my own video
      }
      channelRepository.findByIds(List.of(video.channelId())).stream()
          .findFirst()
          .ifPresent(
              owner ->
                  notifications.create(
                      Notification.videoComment(
                          UUID.randomUUID(),
                          owner.userId(),
                          author.id(),
                          video.id(),
                          comment.id(),
                          clock.instant())));
    } else if (!parent.isAuthoredBy(author.userId())) {
      notifications.create(
          Notification.commentReply(
              UUID.randomUUID(),
              parent.userId(),
              author.id(),
              video.id(),
              comment.id(),
              clock.instant()));
    }
  }
}
