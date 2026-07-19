package com.streamtube.application.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.social.result.CommentView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.notification.Notification;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.notification.NotificationType;
import com.streamtube.domain.shared.SocialExceptions.InvalidCommentContentException;
import com.streamtube.domain.shared.SocialExceptions.InvalidParentCommentException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotPublishedException;
import com.streamtube.domain.social.Comment;
import com.streamtube.domain.social.CommentRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Covers the published-only write rule and single-level reply validation. */
class CreateCommentUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private VideoRepository videos;
  private CommentRepository comments;
  private ChannelRepository channels;
  private NotificationRepository notifications;
  private CreateCommentUseCase useCase;
  private UUID ownerUserId;
  private UUID channelId;
  private UUID videoId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    comments = Mockito.mock(CommentRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    notifications = Mockito.mock(NotificationRepository.class);
    useCase =
        new CreateCommentUseCase(
            videos,
            comments,
            channels,
            notifications,
            new InteractionAccess(channels),
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(comments.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ownerUserId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    videoId = UUID.randomUUID();
    when(channels.findByUserId(ownerUserId))
        .thenReturn(
            Optional.of(Channel.createForUser(channelId, ownerUserId, "name", "nick", NOW)));
  }

  private Video video(boolean published) {
    Video v = Video.initiate(videoId, channelId, "T", "slug123", "videos/slug123", NOW);
    v.markReady(12.5, null, "{}", NOW);
    if (published) {
      v.publish(NOW);
    }
    when(videos.findById(videoId)).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  void commentsOnPublishedVideo() {
    video(true);
    CommentView view = useCase.execute(videoId, ownerUserId, "Primeiro!", null);
    assertThat(view.content()).isEqualTo("Primeiro!");
    assertThat(view.author().nickname()).isEqualTo("nick");
    verify(comments).save(any());
  }

  @Test
  void commentOnOwnVideoIsNotNotified() {
    video(true);
    useCase.execute(videoId, ownerUserId, "no meu proprio", null);
    verify(notifications, never()).create(any());
  }

  @Test
  void topLevelCommentNotifiesVideoOwner() {
    video(true);
    UUID commenterUserId = UUID.randomUUID();
    UUID commenterChannelId = UUID.randomUUID();
    when(channels.findByUserId(commenterUserId))
        .thenReturn(
            Optional.of(Channel.createForUser(commenterChannelId, commenterUserId, "V", "v", NOW)));
    when(channels.findByIds(List.of(channelId)))
        .thenReturn(List.of(Channel.createForUser(channelId, ownerUserId, "name", "nick", NOW)));

    useCase.execute(videoId, commenterUserId, "gostei!", null);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).create(captor.capture());
    Notification n = captor.getValue();
    assertThat(n.type()).isEqualTo(NotificationType.VIDEO_COMMENT);
    assertThat(n.recipientUserId()).isEqualTo(ownerUserId);
    assertThat(n.actorChannelId()).isEqualTo(commenterChannelId);
    assertThat(n.videoId()).isEqualTo(videoId);
    assertThat(n.commentId()).isNotNull();
  }

  @Test
  void replyNotifiesParentAuthor() {
    video(true);
    UUID parentAuthorId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    when(comments.findById(parentId))
        .thenReturn(Optional.of(Comment.create(parentId, videoId, parentAuthorId, null, "raiz", NOW)));

    useCase.execute(videoId, ownerUserId, "respondendo", parentId);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).create(captor.capture());
    Notification n = captor.getValue();
    assertThat(n.type()).isEqualTo(NotificationType.COMMENT_REPLY);
    assertThat(n.recipientUserId()).isEqualTo(parentAuthorId);
    assertThat(n.actorChannelId()).isEqualTo(channelId);
    assertThat(n.commentId()).isNotNull();
  }

  @Test
  void replyToOwnCommentIsNotNotified() {
    video(true);
    UUID parentId = UUID.randomUUID();
    when(comments.findById(parentId))
        .thenReturn(Optional.of(Comment.create(parentId, videoId, ownerUserId, null, "raiz", NOW)));

    useCase.execute(videoId, ownerUserId, "respondendo a mim", parentId);

    verify(notifications, never()).create(any());
  }

  @Test
  void repliesToTopLevelCommentOfTheSameVideo() {
    video(true);
    UUID parentId = UUID.randomUUID();
    when(comments.findById(parentId))
        .thenReturn(
            Optional.of(Comment.create(parentId, videoId, UUID.randomUUID(), null, "raiz", NOW)));

    CommentView view = useCase.execute(videoId, ownerUserId, "resposta", parentId);

    assertThat(view.parentId()).isEqualTo(parentId);
  }

  @Test
  void replyToAReplyIsRejected() {
    video(true);
    UUID replyId = UUID.randomUUID();
    when(comments.findById(replyId))
        .thenReturn(
            Optional.of(
                Comment.create(
                    replyId, videoId, UUID.randomUUID(), UUID.randomUUID(), "resposta", NOW)));

    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId, "de novo", replyId))
        .isInstanceOf(InvalidParentCommentException.class);
    verify(comments, never()).save(any());
  }

  @Test
  void parentOfAnotherVideoIsRejected() {
    video(true);
    UUID parentId = UUID.randomUUID();
    when(comments.findById(parentId))
        .thenReturn(
            Optional.of(
                Comment.create(parentId, UUID.randomUUID(), UUID.randomUUID(), null, "raiz", NOW)));

    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId, "cruzado", parentId))
        .isInstanceOf(InvalidParentCommentException.class);
  }

  @Test
  void ownDraftIsConflictForTheOwner() {
    video(false);
    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId, "cedo demais", null))
        .isInstanceOf(VideoNotPublishedException.class);
  }

  @Test
  void othersDraftIs404() {
    video(false);
    UUID otherUserId = UUID.randomUUID();
    when(channels.findByUserId(otherUserId))
        .thenReturn(
            Optional.of(
                Channel.createForUser(UUID.randomUUID(), otherUserId, "other", "other", NOW)));

    assertThatThrownBy(() -> useCase.execute(videoId, otherUserId, "invasor", null))
        .isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void blankContentIsRejected() {
    video(true);
    assertThatThrownBy(() -> useCase.execute(videoId, ownerUserId, "   ", null))
        .isInstanceOf(InvalidCommentContentException.class);
  }
}
