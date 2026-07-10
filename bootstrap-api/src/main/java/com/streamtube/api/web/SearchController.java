package com.streamtube.api.web;

import com.streamtube.api.web.dto.PageResponse;
import com.streamtube.api.web.dto.VideoDtos.VideoCardResponse;
import com.streamtube.application.video.SearchVideosUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "search", description = "Search published videos by title or channel name")
public class SearchController {

  private final SearchVideosUseCase searchVideos;

  public SearchController(SearchVideosUseCase searchVideos) {
    this.searchVideos = searchVideos;
  }

  @GetMapping
  @Operation(summary = "Search: case-insensitive contains on video title or channel name")
  public PageResponse<VideoCardResponse> search(
      @RequestParam(name = "q") String q,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return PageResponse.from(searchVideos.execute(q, page, size), VideoCardResponses::from);
  }
}
