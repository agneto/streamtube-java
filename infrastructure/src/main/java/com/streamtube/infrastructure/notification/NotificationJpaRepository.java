package com.streamtube.infrastructure.notification;

import com.streamtube.domain.notification.NotificationFeedRow;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

  long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

  /**
   * NEW_VIDEO fan-out: one set-based insert, a row per subscriber of the channel. Native on purpose
   * — it joins the social {@code subscriptions} table (API-only, exactly like Phase 06's
   * {@code findSubscriptionFeed}) and is not validated at the worker's startup. {@code
   * gen_random_uuid()} is the Postgres 13+ built-in.
   *
   * @return the number of subscribers notified
   */
  @Modifying
  @Query(
      value =
          "insert into notifications"
              + " (id, recipient_user_id, type, actor_channel_id, video_id, created_at)"
              + " select gen_random_uuid(), s.user_id, 'NEW_VIDEO', :channelId, :videoId, :at"
              + " from subscriptions s where s.channel_id = :channelId",
      nativeQuery = true)
  int fanOutNewVideo(
      @Param("channelId") UUID channelId,
      @Param("videoId") UUID videoId,
      @Param("at") Instant at);

  /** Recipient-scoped: flips only if the row is theirs and still unread. */
  @Modifying
  @Query(
      "update NotificationEntity n set n.readAt = :now"
          + " where n.id = :id and n.recipientUserId = :recipientUserId and n.readAt is null")
  int markRead(
      @Param("id") UUID id,
      @Param("recipientUserId") UUID recipientUserId,
      @Param("now") Instant now);

  @Modifying
  @Query(
      "update NotificationEntity n set n.readAt = :now"
          + " where n.recipientUserId = :recipientUserId and n.readAt is null")
  int markAllRead(@Param("recipientUserId") UUID recipientUserId, @Param("now") Instant now);

  /**
   * Recipient's feed, newest first, with the actor channel, video and comment left-joined in one
   * query (any subject may be absent for a given type). JPQL ad-hoc joins keep it validated at API
   * startup; the worker never maps {@code NotificationEntity}, so it never loads this repository.
   */
  @Query(
      value =
          "select new com.streamtube.domain.notification.NotificationFeedRow("
              + " n.id, n.type, n.readAt, n.createdAt,"
              + " c.id, c.name, c.nickname,"
              + " v.id, v.slug, v.title, v.thumbnailKey,"
              + " cm.id, cm.content)"
              + " from NotificationEntity n"
              + " left join ChannelEntity c on c.id = n.actorChannelId"
              + " left join VideoEntity v on v.id = n.videoId"
              + " left join CommentEntity cm on cm.id = n.commentId"
              + " where n.recipientUserId = :recipientUserId"
              + " order by n.createdAt desc",
      countQuery =
          "select count(n) from NotificationEntity n where n.recipientUserId = :recipientUserId")
  Page<NotificationFeedRow> findFeed(
      @Param("recipientUserId") UUID recipientUserId, Pageable pageable);
}
