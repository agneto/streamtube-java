package com.streamtube.application.notification;

import com.streamtube.domain.notification.NotificationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Unread badge count for the caller — backed by the partial index on {@code read_at IS NULL}. */
@Service
public class GetUnreadCountUseCase {

  private final NotificationRepository notifications;

  public GetUnreadCountUseCase(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public long execute(UUID userId) {
    return notifications.unreadCount(userId);
  }
}
