package com.streamtube.application.video;

import com.streamtube.application.video.result.VideoCardView;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.shared.VideoExceptions.InvalidSearchQueryException;
import com.streamtube.domain.video.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search bar: case-insensitive "contains" over video title or owning channel name, published +
 * PUBLIC only, newest first. No relevance ranking — recorded trade-off of the phase plan.
 */
@Service
public class SearchVideosUseCase {

  private static final int MIN_QUERY_LENGTH = 2;

  private final VideoRepository videoRepository;
  private final VideoCards cards;

  public SearchVideosUseCase(VideoRepository videoRepository, VideoCards cards) {
    this.videoRepository = videoRepository;
    this.cards = cards;
  }

  @Transactional(readOnly = true)
  public PageResult<VideoCardView> execute(String query, int page, int size) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.length() < MIN_QUERY_LENGTH) {
      throw new InvalidSearchQueryException();
    }
    return cards.toCards(
        videoRepository.searchListed(trimmed, PageRequests.page(page), PageRequests.size(size)));
  }
}
