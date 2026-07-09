package com.streamtube.application.social;

import com.streamtube.application.social.result.CommentView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.social.Comment;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Resolves comment authors' channels in one batch query per page (no N+1). */
@Component
class CommentAuthors {

  private final ChannelRepository channelRepository;

  CommentAuthors(ChannelRepository channelRepository) {
    this.channelRepository = channelRepository;
  }

  PageResult<CommentView> toViews(PageResult<Comment> comments) {
    Map<UUID, Channel> byUserId =
        channelRepository
            .findByUserIds(comments.items().stream().map(Comment::userId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Channel::userId, Function.identity()));
    return comments.map(c -> CommentView.from(c, byUserId.get(c.userId())));
  }
}
