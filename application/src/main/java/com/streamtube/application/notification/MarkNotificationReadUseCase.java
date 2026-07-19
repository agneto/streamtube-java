package com.streamtube.application.notification;

import com.streamtube.domain.notification.NotificationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks one notification read. Recipient-scoped: a foreign id flips nothing (no IDOR) and the call
 * is idempotent (already-read is a no-op) — both surface as HTTP 204 regardless.
 */
@Service
public class MarkNotificationReadUseCase {

  private final NotificationRepository notifications;

  public MarkNotificationReadUseCase(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional
  public void execute(UUID id, UUID userId) {
    notifications.markRead(id, userId);
  }
}
