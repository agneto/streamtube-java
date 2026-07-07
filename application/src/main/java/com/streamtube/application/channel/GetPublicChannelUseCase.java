package com.streamtube.application.channel;

import com.streamtube.application.channel.result.PublicChannelView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.ChannelExceptions.ChannelNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public channel page header, looked up by nickname. */
@Service
public class GetPublicChannelUseCase {

  private final ChannelRepository channelRepository;

  public GetPublicChannelUseCase(ChannelRepository channelRepository) {
    this.channelRepository = channelRepository;
  }

  @Transactional(readOnly = true)
  public PublicChannelView execute(String nickname) {
    Channel channel =
        channelRepository.findByNickname(nickname).orElseThrow(ChannelNotFoundException::new);
    return new PublicChannelView(
        channel.id(),
        channel.name(),
        channel.nickname(),
        channel.description(),
        channel.createdAt());
  }
}
