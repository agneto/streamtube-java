package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.shared.PageResult;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.domain.video.Visibility;
import com.streamtube.infrastructure.persistence.entity.VideoEntity;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.VideoJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class VideoRepositoryAdapter implements VideoRepository {

  private final VideoJpaRepository jpa;

  public VideoRepositoryAdapter(VideoJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Video save(Video video) {
    return PersistenceMapper.toDomain(jpa.save(PersistenceMapper.toEntity(video)));
  }

  @Override
  public Optional<Video> findById(UUID id) {
    return jpa.findById(id).map(PersistenceMapper::toDomain);
  }

  @Override
  public Optional<Video> findBySlug(String slug) {
    return jpa.findBySlug(slug).map(PersistenceMapper::toDomain);
  }

  @Override
  public boolean existsBySlug(String slug) {
    return jpa.existsBySlug(slug);
  }

  @Override
  public PageResult<Video> findPageByChannelId(UUID channelId, int page, int size) {
    return toPageResult(
        jpa.findByChannelIdOrderByCreatedAtDesc(channelId, PageRequest.of(page, size)),
        page,
        size);
  }

  @Override
  public PageResult<Video> findPublishedPublicPageByChannelId(UUID channelId, int page, int size) {
    return toPageResult(
        jpa.findByChannelIdAndVisibilityAndPublishedAtNotNullOrderByPublishedAtDesc(
            channelId, Visibility.PUBLIC, PageRequest.of(page, size)),
        page,
        size);
  }

  @Override
  public void incrementViews(UUID id) {
    jpa.incrementViews(id);
  }

  @Override
  public List<Video> findRelatedByCategory(UUID categoryId, UUID excludeId, int limit) {
    return jpa
        .findByCategoryIdAndIdNotAndVisibilityAndPublishedAtNotNullOrderByPublishedAtDesc(
            categoryId, excludeId, Visibility.PUBLIC, PageRequest.of(0, limit))
        .stream()
        .map(PersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public List<Video> findLatestListed(UUID excludeId, int limit) {
    return jpa
        .findByIdNotAndVisibilityAndPublishedAtNotNullOrderByPublishedAtDesc(
            excludeId, Visibility.PUBLIC, PageRequest.of(0, limit))
        .stream()
        .map(PersistenceMapper::toDomain)
        .toList();
  }

  private static PageResult<Video> toPageResult(Page<VideoEntity> result, int page, int size) {
    return new PageResult<>(
        result.getContent().stream().map(PersistenceMapper::toDomain).toList(),
        page,
        size,
        result.getTotalElements());
  }
}
