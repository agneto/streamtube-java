package com.streamtube.api.web;

import com.streamtube.api.web.dto.PageResponse;
import com.streamtube.api.web.dto.SocialDtos.SubscriptionResponse;
import com.streamtube.api.web.dto.VideoDtos.VideoSummaryResponse;
import com.streamtube.application.social.GetSubscriptionFeedUseCase;
import com.streamtube.application.social.ListMySubscriptionsUseCase;
import com.streamtube.application.video.result.VideoSummaryView;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "subscriptions", description = "Subscribed-channels area: channel list and video feed")
public class SubscriptionsController {

  private final ListMySubscriptionsUseCase listMySubscriptions;
  private final GetSubscriptionFeedUseCase getSubscriptionFeed;

  public SubscriptionsController(
      ListMySubscriptionsUseCase listMySubscriptions,
      GetSubscriptionFeedUseCase getSubscriptionFeed) {
    this.listMySubscriptions = listMySubscriptions;
    this.getSubscriptionFeed = getSubscriptionFeed;
  }

  @GetMapping
  @Operation(summary = "Channels I subscribe to, most recently subscribed first")
  public PageResponse<SubscriptionResponse> mySubscriptions(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(
        listMySubscriptions.execute(principal.id(), page, size),
        SocialResponses::toSubscription);
  }

  @GetMapping("/videos")
  @Operation(summary = "Feed: latest published PUBLIC videos of my subscribed channels")
  public PageResponse<VideoSummaryResponse> feed(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(
        getSubscriptionFeed.execute(principal.id(), page, size), SubscriptionsController::toSummary);
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
