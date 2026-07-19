package com.streamtube.application.notification;

import com.streamtube.domain.notification.NotificationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marks every unread notification of the caller read. Recipient-scoped; idempotent. */
@Service
public class MarkAllNotificationsReadUseCase {

  private final NotificationRepository notifications;

  public MarkAllNotificationsReadUseCase(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional
  public void execute(UUID userId) {
    notifications.markAllRead(userId);
  }
}
