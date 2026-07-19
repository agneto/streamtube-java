package com.streamtube.infrastructure.notification;

import com.streamtube.domain.notification.Notification;
import com.streamtube.domain.notification.NotificationFeedRow;
import com.streamtube.domain.notification.NotificationRepository;
import com.streamtube.domain.shared.PageResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** API-only notification persistence. Writes ride the caller's transaction; cleanup is FK cascade. */
@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

  private final NotificationJpaRepository jpa;

  public NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void create(Notification n) {
    jpa.save(
        new NotificationEntity(
            n.id(),
            n.recipientUserId(),
            n.type(),
            n.actorChannelId(),
            n.videoId(),
            n.commentId(),
            n.readAt(),
            n.createdAt()));
  }

  @Override
  public int fanOutNewVideo(UUID channelId, UUID videoId, Instant at) {
    return jpa.fanOutNewVideo(channelId, videoId, at);
  }

  @Override
  public long unreadCount(UUID recipientUserId) {
    return jpa.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
  }

  @Override
  public boolean markRead(UUID id, UUID recipientUserId) {
    return jpa.markRead(id, recipientUserId, Instant.now()) == 1;
  }

  @Override
  public int markAllRead(UUID recipientUserId) {
    return jpa.markAllRead(recipientUserId, Instant.now());
  }

  @Override
  public PageResult<NotificationFeedRow> findPage(UUID recipientUserId, int page, int size) {
    Page<NotificationFeedRow> result =
        jpa.findFeed(recipientUserId, PageRequest.of(page, size));
    return new PageResult<>(result.getContent(), page, size, result.getTotalElements());
  }
}
