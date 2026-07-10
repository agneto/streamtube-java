package com.streamtube.api.web;

import com.streamtube.api.web.dto.VideoDtos.CreateVideoRequest;
import com.streamtube.api.web.dto.VideoDtos.InitiateUploadResponse;
import com.streamtube.api.web.dto.VideoDtos.ThumbnailUploadRequest;
import com.streamtube.api.web.dto.VideoDtos.ThumbnailUploadResponse;
import com.streamtube.api.web.dto.PageResponse;
import com.streamtube.api.web.dto.SocialDtos.CommentResponse;
import com.streamtube.api.web.dto.SocialDtos.CreateCommentRequest;
import com.streamtube.api.web.dto.SocialDtos.ReactionRequest;
import com.streamtube.api.web.dto.VideoDtos.UpdateVideoRequest;
import com.streamtube.api.web.dto.VideoDtos.VideoCardResponse;
import com.streamtube.api.web.dto.VideoDtos.VideoInfoResponse;
import com.streamtube.api.web.dto.VideoDtos.VideoSummaryResponse;
import com.streamtube.application.social.CreateCommentUseCase;
import com.streamtube.application.social.ListCommentsUseCase;
import com.streamtube.application.social.RemoveVideoReactionUseCase;
import com.streamtube.application.social.SetVideoReactionUseCase;
import com.streamtube.application.video.CompleteThumbnailUploadUseCase;
import com.streamtube.application.video.ListHomeVideosUseCase;
import com.streamtube.application.video.CompleteUploadUseCase;
import com.streamtube.application.video.GetDownloadUrlUseCase;
import com.streamtube.application.video.GetRelatedVideosUseCase;
import com.streamtube.application.video.GetStreamUrlUseCase;
import com.streamtube.application.video.GetVideoInfoUseCase;
import com.streamtube.application.video.InitiateThumbnailUploadUseCase;
import com.streamtube.application.video.InitiateUploadUseCase;
import com.streamtube.application.video.PublishVideoUseCase;
import com.streamtube.application.video.UpdateVideoDetailsUseCase;
import com.streamtube.application.video.result.InitiateUploadResult;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/videos")
@Tag(name = "videos", description = "Video upload, processing, management, streaming and download")
public class VideosController {

  private final InitiateUploadUseCase initiateUpload;
  private final CompleteUploadUseCase completeUpload;
  private final GetVideoInfoUseCase getVideoInfo;
  private final GetStreamUrlUseCase getStreamUrl;
  private final GetDownloadUrlUseCase getDownloadUrl;
  private final UpdateVideoDetailsUseCase updateVideoDetails;
  private final PublishVideoUseCase publishVideo;
  private final InitiateThumbnailUploadUseCase initiateThumbnailUpload;
  private final CompleteThumbnailUploadUseCase completeThumbnailUpload;
  private final GetRelatedVideosUseCase getRelatedVideos;
  private final SetVideoReactionUseCase setVideoReaction;
  private final RemoveVideoReactionUseCase removeVideoReaction;
  private final CreateCommentUseCase createComment;
  private final ListCommentsUseCase listComments;
  private final ListHomeVideosUseCase listHomeVideos;

  public VideosController(
      InitiateUploadUseCase initiateUpload,
      CompleteUploadUseCase completeUpload,
      GetVideoInfoUseCase getVideoInfo,
      GetStreamUrlUseCase getStreamUrl,
      GetDownloadUrlUseCase getDownloadUrl,
      UpdateVideoDetailsUseCase updateVideoDetails,
      PublishVideoUseCase publishVideo,
      InitiateThumbnailUploadUseCase initiateThumbnailUpload,
      CompleteThumbnailUploadUseCase completeThumbnailUpload,
      GetRelatedVideosUseCase getRelatedVideos,
      SetVideoReactionUseCase setVideoReaction,
      RemoveVideoReactionUseCase removeVideoReaction,
      CreateCommentUseCase createComment,
      ListCommentsUseCase listComments,
      ListHomeVideosUseCase listHomeVideos) {
    this.initiateUpload = initiateUpload;
    this.completeUpload = completeUpload;
    this.getVideoInfo = getVideoInfo;
    this.getStreamUrl = getStreamUrl;
    this.getDownloadUrl = getDownloadUrl;
    this.updateVideoDetails = updateVideoDetails;
    this.publishVideo = publishVideo;
    this.initiateThumbnailUpload = initiateThumbnailUpload;
    this.completeThumbnailUpload = completeThumbnailUpload;
    this.getRelatedVideos = getRelatedVideos;
    this.setVideoReaction = setVideoReaction;
    this.removeVideoReaction = removeVideoReaction;
    this.createComment = createComment;
    this.listComments = listComments;
    this.listHomeVideos = listHomeVideos;
  }

  @GetMapping
  @Operation(summary = "Home grid: published PUBLIC videos, newest first, optional category")
  public PageResponse<VideoCardResponse> list(
      @RequestParam(name = "categoryId", required = false) UUID categoryId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(
        listHomeVideos.execute(categoryId, page, size), VideoCardResponses::from);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Initiate a video upload (returns a presigned PUT URL)")
  public InitiateUploadResponse initiate(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateVideoRequest request) {
    InitiateUploadResult result =
        initiateUpload.execute(
            principal.id(), request.title(), request.sizeBytes(), request.contentType());
    return new InitiateUploadResponse(result.id(), result.slug(), result.uploadUrl());
  }

  @PostMapping("/{id}/complete-upload")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Confirm the upload completed and enqueue processing")
  public void complete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
    completeUpload.execute(id, principal.id());
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update a video's title/description/category/visibility (owner only)")
  public VideoInfoResponse update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateVideoRequest request) {
    return toResponse(
        updateVideoDetails.execute(
            id,
            principal.id(),
            new UpdateVideoDetailsUseCase.Command(
                request.title(), request.description(), request.categoryId(),
                request.visibility())));
  }

  @PostMapping("/{id}/publish")
  @Operation(summary = "Publish a READY draft video (idempotent, owner only)")
  public VideoInfoResponse publish(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
    return toResponse(publishVideo.execute(id, principal.id()));
  }

  @PostMapping("/{id}/thumbnail")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Initiate a custom thumbnail upload (returns a presigned PUT URL)")
  public ThumbnailUploadResponse initiateThumbnail(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ThumbnailUploadRequest request) {
    return new ThumbnailUploadResponse(
        initiateThumbnailUpload.execute(
            id, principal.id(), request.sizeBytes(), request.contentType()));
  }

  @PostMapping("/{id}/thumbnail/complete")
  @Operation(summary = "Confirm the custom thumbnail upload and swap the video's thumbnail")
  public VideoInfoResponse completeThumbnail(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
    return toResponse(completeThumbnailUpload.execute(id, principal.id()));
  }

  @GetMapping("/{slug}")
  @Operation(summary = "Get video info (drafts are visible to their owner only)")
  public VideoInfoResponse info(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("slug") String slug) {
    return toResponse(getVideoInfo.execute(slug, viewerId(principal)));
  }

  @PutMapping("/{id}/reaction")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Set/switch my reaction on a published video (LIKE | DISLIKE)")
  public void react(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ReactionRequest request) {
    setVideoReaction.execute(id, principal.id(), request.type());
  }

  @DeleteMapping("/{id}/reaction")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove my reaction from a published video (idempotent)")
  public void unreact(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
    removeVideoReaction.execute(id, principal.id());
  }

  @PostMapping("/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Comment on a published video (parentId = single-level reply)")
  public CommentResponse comment(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("id") UUID id,
      @Valid @RequestBody CreateCommentRequest request) {
    return SocialResponses.toComment(
        createComment.execute(id, principal.id(), request.content(), request.parentId()));
  }

  @GetMapping("/{slug}/comments")
  @Operation(summary = "Top-level comments of a video, newest first (public)")
  public PageResponse<CommentResponse> comments(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("slug") String slug,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(
        listComments.execute(slug, viewerId(principal), page, size), SocialResponses::toComment);
  }

  @GetMapping("/{slug}/related")
  @Operation(summary = "Watch-page suggestions: same-category published PUBLIC videos")
  public List<VideoSummaryResponse> related(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("slug") String slug,
      @RequestParam(name = "limit", defaultValue = "10") int limit) {
    return getRelatedVideos.execute(slug, viewerId(principal), limit).stream()
        .map(VideosController::toSummary)
        .toList();
  }

  @GetMapping("/{slug}/stream")
  @Operation(summary = "Redirect (302) to a presigned streaming URL")
  public ResponseEntity<Void> stream(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("slug") String slug) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(getStreamUrl.execute(slug, viewerId(principal))))
        .build();
  }

  @GetMapping("/{slug}/download")
  @Operation(summary = "Redirect (302) to a presigned download URL")
  public ResponseEntity<Void> download(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("slug") String slug) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(getDownloadUrl.execute(slug, viewerId(principal))))
        .build();
  }

  /** Read endpoints are public: the principal is null for anonymous requests. */
  private static UUID viewerId(AuthenticatedUser principal) {
    return principal == null ? null : principal.id();
  }

  private VideoInfoResponse toResponse(VideoInfoView v) {
    return new VideoInfoResponse(
        v.id(),
        v.slug(),
        v.title(),
        v.status(),
        v.description(),
        v.categoryId(),
        v.visibility(),
        v.publishedAt(),
        v.thumbnailUrl(),
        v.durationSeconds(),
        v.views(),
        v.likes(),
        v.dislikes(),
        v.commentsCount(),
        v.myReaction(),
        v.channelId(),
        v.createdAt());
  }

  private static VideoSummaryResponse toSummary(VideoSummaryView v) {
    return new VideoSummaryResponse(
        v.id(),
        v.slug(),
        v.title(),
        v.status(),
        v.visibility(),
        v.publishedAt(),
        v.thumbnailUrl(),
        v.durationSeconds(),
        v.views(),
        v.createdAt());
  }
}
