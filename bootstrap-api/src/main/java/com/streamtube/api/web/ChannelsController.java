package com.streamtube.api.web;

import com.streamtube.api.web.dto.ChannelDtos.ChannelInfoResponse;
import com.streamtube.api.web.dto.ChannelDtos.UpdateChannelRequest;
import com.streamtube.application.channel.UpdateChannelDescriptionUseCase;
import com.streamtube.application.channel.result.ChannelInfoView;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/channels")
@Tag(name = "channels", description = "Channel management")
public class ChannelsController {

  private final UpdateChannelDescriptionUseCase updateChannelDescription;

  public ChannelsController(UpdateChannelDescriptionUseCase updateChannelDescription) {
    this.updateChannelDescription = Objects.requireNonNull(updateChannelDescription);
  }

  @PatchMapping("/me")
  @Operation(summary = "Update the description of the logged-in user's channel")
  public ChannelInfoResponse updateDescription(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody UpdateChannelRequest request) {
    return toResponse(
        updateChannelDescription.execute(principal.id(), request.description()));
  }

  private ChannelInfoResponse toResponse(ChannelInfoView v) {
    return new ChannelInfoResponse(
        v.id(), v.userId(), v.name(), v.nickname(), v.description(), v.createdAt(), v.updatedAt());
  }
}
