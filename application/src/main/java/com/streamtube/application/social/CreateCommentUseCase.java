package com.streamtube.application.social;

import com.streamtube.application.social.result.CommentView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import com.streamtube.domain.shared.SocialExceptions.InvalidParentCommentException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.social.Comment;
import com.streamtube.domain.social.CommentRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
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
  private final InteractionAccess access;
  private final Clock clock;

  public CreateCommentUseCase(
      VideoRepository videoRepository,
      CommentRepository commentRepository,
      ChannelRepository channelRepository,
      InteractionAccess access,
      Clock clock) {
    this.videoRepository = videoRepository;
    this.commentRepository = commentRepository;
    this.channelRepository = channelRepository;
    this.access = access;
    this.clock = clock;
  }

  @Transactional
  public CommentView execute(UUID videoId, UUID userId, String content, UUID parentId) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);
    access.ensureInteractable(video, userId);
    if (parentId != null) {
      Comment parent =
          commentRepository.findById(parentId).orElseThrow(InvalidParentCommentException::new);
      if (parent.isReply() || !parent.videoId().equals(videoId)) {
        throw new InvalidParentCommentException();
      }
    }
    Comment comment =
        commentRepository.save(
            Comment.create(UUID.randomUUID(), videoId, userId, parentId, content, clock.instant()));
    Channel author =
        channelRepository.findByUserId(userId).orElseThrow(ChannelNotFoundException::new);
    return CommentView.from(comment, author);
  }
}
