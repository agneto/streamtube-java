package com.streamtube.api.web;

import com.streamtube.api.web.dto.NotificationDtos.ActorResponse;
import com.streamtube.api.web.dto.NotificationDtos.CommentRefResponse;
import com.streamtube.api.web.dto.NotificationDtos.NotificationResponse;
import com.streamtube.api.web.dto.NotificationDtos.UnreadCountResponse;
import com.streamtube.api.web.dto.NotificationDtos.VideoRefResponse;
import com.streamtube.api.web.dto.PageResponse;
import com.streamtube.application.notification.GetUnreadCountUseCase;
import com.streamtube.application.notification.ListNotificationsUseCase;
import com.streamtube.application.notification.MarkAllNotificationsReadUseCase;
import com.streamtube.application.notification.MarkNotificationReadUseCase;
import com.streamtube.application.notification.result.NotificationView;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "notifications", description = "My in-app notification feed and read state")
public class NotificationsController {

  private final ListNotificationsUseCase listNotifications;
  private final GetUnreadCountUseCase getUnreadCount;
  private final MarkNotificationReadUseCase markRead;
  private final MarkAllNotificationsReadUseCase markAllRead;

  public NotificationsController(
      ListNotificationsUseCase listNotifications,
      GetUnreadCountUseCase getUnreadCount,
      MarkNotificationReadUseCase markRead,
      MarkAllNotificationsReadUseCase markAllRead) {
    this.listNotifications = listNotifications;
    this.getUnreadCount = getUnreadCount;
    this.markRead = markRead;
    this.markAllRead = markAllRead;
  }

  @GetMapping
  @Operation(summary = "My notifications, newest first")
  public PageResponse<NotificationResponse> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(
        listNotifications.execute(principal.id(), page, size), NotificationsController::toResponse);
  }

  @GetMapping("/unread-count")
  @Operation(summary = "How many of my notifications are unread")
  public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
    return new UnreadCountResponse(getUnreadCount.execute(principal.id()));
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Mark one of my notifications read (idempotent; foreign ids are no-ops)")
  public void read(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
    markRead.execute(id, principal.id());
  }

  @PostMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Mark all my notifications read")
  public void readAll(@AuthenticationPrincipal AuthenticatedUser principal) {
    markAllRead.execute(principal.id());
  }

  private static NotificationResponse toResponse(NotificationView v) {
    return new NotificationResponse(
        v.id(),
        v.type(),
        v.read(),
        v.createdAt(),
        v.actor() == null
            ? null
            : new ActorResponse(v.actor().channelId(), v.actor().name(), v.actor().nickname()),
        v.video() == null
            ? null
            : new VideoRefResponse(
                v.video().id(), v.video().slug(), v.video().title(), v.video().thumbnailUrl()),
        v.comment() == null
            ? null
            : new CommentRefResponse(v.comment().id(), v.comment().content()));
  }
}
