package com.streamtube.application.notification;

import com.streamtube.application.notification.result.NotificationView;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.shared.PageResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's notification feed, newest first. Recipient-scoped; presigns video thumbnails. */
@Service
public class ListNotificationsUseCase {

  private static final int MAX_PAGE_SIZE = 100;

  private final NotificationRepository notifications;
  private final StoragePort storage;

  public ListNotificationsUseCase(NotificationRepository notifications, StoragePort storage) {
    this.notifications = notifications;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public PageResult<NotificationView> execute(UUID userId, int page, int size) {
    return notifications
        .findPage(userId, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE))
        .map(
            row ->
                NotificationView.from(
                    row,
                    row.videoThumbnailKey() == null
                        ? null
                        : storage.presignStream(row.videoThumbnailKey())));
  }
}
