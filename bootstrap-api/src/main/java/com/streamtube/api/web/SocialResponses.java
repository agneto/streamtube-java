package com.streamtube.api.web;

import com.streamtube.api.web.dto.SocialDtos.CommentAuthorResponse;
import com.streamtube.api.web.dto.SocialDtos.CommentResponse;
import com.streamtube.api.web.dto.SocialDtos.SubscriptionResponse;
import com.streamtube.application.social.result.CommentView;
import com.streamtube.application.social.result.SubscriptionView;

/** View → response mapping shared by the social controllers. */
final class SocialResponses {

  private SocialResponses() {}

  static CommentResponse toComment(CommentView v) {
    return new CommentResponse(
        v.id(),
        v.videoId(),
        v.parentId(),
        v.content(),
        v.author() == null
            ? null
            : new CommentAuthorResponse(
                v.author().channelId(), v.author().name(), v.author().nickname()),
        v.likes(),
        v.dislikes(),
        v.repliesCount(),
        v.createdAt());
  }

  static SubscriptionResponse toSubscription(SubscriptionView v) {
    return new SubscriptionResponse(
        v.channelId(),
        v.name(),
        v.nickname(),
        v.description(),
        v.subscribersCount(),
        v.subscribedAt());
  }
}
