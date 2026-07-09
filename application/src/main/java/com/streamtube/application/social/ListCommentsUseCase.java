package com.streamtube.application.social;

import com.streamtube.application.social.result.CommentView;
import com.streamtube.application.video.VideoViewAccess;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.social.CommentRepository;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Top-level comments of a video, newest first. Public read: follows the video read rule (drafts
 * are owner-only and answer 404 to anyone else).
 */
@Service
public class ListCommentsUseCase {

  private final VideoRepository videoRepository;
  private final CommentRepository commentRepository;
  private final VideoViewAccess access;
  private final CommentAuthors authors;

  public ListCommentsUseCase(
      VideoRepository videoRepository,
      CommentRepository commentRepository,
      VideoViewAccess access,
      CommentAuthors authors) {
    this.videoRepository = videoRepository;
    this.commentRepository = commentRepository;
    this.access = access;
    this.authors = authors;
  }

  @Transactional(readOnly = true)
  public PageResult<CommentView> execute(String slug, UUID viewerUserId, int page, int size) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    access.ensureViewable(video, viewerUserId);
    return authors.toViews(
        commentRepository.findTopLevelByVideoId(
            video.id(), SocialPageRequests.page(page), SocialPageRequests.size(size)));
  }
}
