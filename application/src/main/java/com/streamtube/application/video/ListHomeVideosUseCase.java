package com.streamtube.application.video;

import com.streamtube.application.video.result.VideoCardView;
import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Home grid: published + PUBLIC platform-wide, newest first, optional category filter. An unknown
 * category simply yields an empty page (not an error) — the frontend filters with ids it got from
 * the categories endpoint anyway.
 */
@Service
public class ListHomeVideosUseCase {

  private final VideoRepository videoRepository;
  private final VideoCards cards;

  public ListHomeVideosUseCase(VideoRepository videoRepository, VideoCards cards) {
    this.videoRepository = videoRepository;
    this.cards = cards;
  }

  @Transactional(readOnly = true)
  public PageResult<VideoCardView> execute(UUID categoryId, int page, int size) {
    return cards.toCards(
        videoRepository.findListedPage(
            categoryId, PageRequests.page(page), PageRequests.size(size)));
  }
}
