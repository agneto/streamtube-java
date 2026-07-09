package com.streamtube.api.web;

import com.streamtube.api.web.dto.ChannelDtos.ChannelInfoResponse;
import com.streamtube.api.web.dto.ChannelDtos.PublicChannelResponse;
import com.streamtube.api.web.dto.ChannelDtos.UpdateChannelRequest;
import com.streamtube.api.web.dto.PageResponse;
import com.streamtube.api.web.dto.VideoDtos.VideoSummaryResponse;
import com.streamtube.application.channel.GetPublicChannelUseCase;
import com.streamtube.application.channel.UpdateChannelInfoUseCase;
import com.streamtube.application.channel.result.ChannelInfoView;
import com.streamtube.application.channel.result.PublicChannelView;
import com.streamtube.application.social.SubscribeUseCase;
import com.streamtube.application.social.UnsubscribeUseCase;
import com.streamtube.application.video.ListChannelVideosUseCase;
import com.streamtube.application.video.ListMyVideosUseCase;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels")
@Tag(name = "channels", description = "Channel management and public channel pages")
public class ChannelsController {

  private final UpdateChannelInfoUseCase updateChannelInfo;
  private final GetPublicChannelUseCase getPublicChannel;
  private final ListMyVideosUseCase listMyVideos;
  private final ListChannelVideosUseCase listChannelVideos;
  private final SubscribeUseCase subscribe;
  private final UnsubscribeUseCase unsubscribe;

  public ChannelsController(
      UpdateChannelInfoUseCase updateChannelInfo,
      GetPublicChannelUseCase getPublicChannel,
      ListMyVideosUseCase listMyVideos,
      ListChannelVideosUseCase listChannelVideos,
      SubscribeUseCase subscribe,
      UnsubscribeUseCase unsubscribe) {
    this.updateChannelInfo = updateChannelInfo;
    this.getPublicChannel = getPublicChannel;
    this.listMyVideos = listMyVideos;
    this.listChannelVideos = listChannelVideos;
    this.subscribe = subscribe;
    this.unsubscribe = unsubscribe;
  }

  @PatchMapping("/me")
  @Operation(summary = "Update the logged-in user's channel (name, nickname, description)")
  public ChannelInfoResponse updateMe(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody UpdateChannelRequest request) {
    return toResponse(
        updateChannelInfo.execute(
            principal.id(),
            new UpdateChannelInfoUseCase.Command(
                request.name(), request.nickname(), request.description())));
  }

  @GetMapping("/me/videos")
  @Operation(summary = "Owner management panel: list my videos (every status/visibility)")
  public PageResponse<VideoSummaryResponse> myVideos(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(
        listMyVideos.execute(principal.id(), page, size), ChannelsController::toSummary);
  }

  @GetMapping("/{nickname}")
  @Operation(summary = "Public channel page header (subscriber count + viewer's subscription)")
  public PublicChannelResponse publicChannel(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("nickname") String nickname) {
    PublicChannelView v = getPublicChannel.execute(nickname, viewerId(principal));
    return new PublicChannelResponse(
        v.id(),
        v.name(),
        v.nickname(),
        v.description(),
        v.subscribersCount(),
        v.subscribed(),
        v.createdAt());
  }

  @PutMapping("/{nickname}/subscription")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Subscribe to a channel (idempotent; own channel is a 400)")
  public void subscribe(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("nickname") String nickname) {
    subscribe.execute(principal.id(), nickname);
  }

  @DeleteMapping("/{nickname}/subscription")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Unsubscribe from a channel (idempotent)")
  public void unsubscribe(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable("nickname") String nickname) {
    unsubscribe.execute(principal.id(), nickname);
  }

  /** The public page is open: the principal is null for anonymous requests. */
  private static UUID viewerId(AuthenticatedUser principal) {
    return principal == null ? null : principal.id();
  }

  @GetMapping("/{nickname}/videos")
  @Operation(summary = "Public channel page: list published PUBLIC videos")
  public PageResponse<VideoSummaryResponse> publicChannelVideos(
      @PathVariable("nickname") String nickname,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(
        listChannelVideos.execute(nickname, page, size), ChannelsController::toSummary);
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

  private ChannelInfoResponse toResponse(ChannelInfoView v) {
    return new ChannelInfoResponse(
        v.id(), v.userId(), v.name(), v.nickname(), v.description(), v.createdAt(), v.updatedAt());
  }
}
