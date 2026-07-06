package com.streamtube.api.web;

import com.streamtube.api.web.dto.VideoDtos.CreateVideoRequest;
import com.streamtube.api.web.dto.VideoDtos.InitiateUploadResponse;
import com.streamtube.api.web.dto.VideoDtos.UpdateVideoRequest;
import com.streamtube.api.web.dto.VideoDtos.VideoInfoResponse;
import com.streamtube.application.video.CompleteUploadUseCase;
import com.streamtube.application.video.GetDownloadUrlUseCase;
import com.streamtube.application.video.GetStreamUrlUseCase;
import com.streamtube.application.video.GetVideoInfoUseCase;
import com.streamtube.application.video.InitiateUploadUseCase;
import com.streamtube.application.video.RenameVideoUseCase;
import com.streamtube.application.video.result.InitiateUploadResult;
import com.streamtube.application.video.result.VideoInfoView;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/videos")
@Tag(name = "videos", description = "Video upload, processing, streaming and download")
public class VideosController {

  private final InitiateUploadUseCase initiateUpload;
  private final CompleteUploadUseCase completeUpload;
  private final GetVideoInfoUseCase getVideoInfo;
  private final GetStreamUrlUseCase getStreamUrl;
  private final GetDownloadUrlUseCase getDownloadUrl;
  private final RenameVideoUseCase renameVideo;

  public VideosController(
      InitiateUploadUseCase initiateUpload,
      CompleteUploadUseCase completeUpload,
      GetVideoInfoUseCase getVideoInfo,
      GetStreamUrlUseCase getStreamUrl,
      GetDownloadUrlUseCase getDownloadUrl,
      RenameVideoUseCase renameVideo) {
    this.initiateUpload = initiateUpload;
    this.completeUpload = completeUpload;
    this.getVideoInfo = getVideoInfo;
    this.getStreamUrl = getStreamUrl;
    this.getDownloadUrl = getDownloadUrl;
    this.renameVideo = renameVideo;
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
  @Operation(summary = "Rename a video (owner only)")
  public VideoInfoResponse rename(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateVideoRequest request) {
    return toResponse(renameVideo.execute(id, principal.id(), request.title()));
  }

  @GetMapping("/{slug}")
  @Operation(summary = "Get public video info")
  public VideoInfoResponse info(@PathVariable("slug") String slug) {
    return toResponse(getVideoInfo.execute(slug));
  }

  private VideoInfoResponse toResponse(VideoInfoView v) {
    return new VideoInfoResponse(
        v.id(),
        v.slug(),
        v.title(),
        v.status(),
        v.thumbnailUrl(),
        v.durationSeconds(),
        v.channelId(),
        v.createdAt());
  }

  @GetMapping("/{slug}/stream")
  @Operation(summary = "Redirect (302) to a presigned streaming URL")
  public ResponseEntity<Void> stream(@PathVariable("slug") String slug) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(getStreamUrl.execute(slug)))
        .build();
  }

  @GetMapping("/{slug}/download")
  @Operation(summary = "Redirect (302) to a presigned download URL")
  public ResponseEntity<Void> download(@PathVariable("slug") String slug) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(getDownloadUrl.execute(slug)))
        .build();
  }
}
