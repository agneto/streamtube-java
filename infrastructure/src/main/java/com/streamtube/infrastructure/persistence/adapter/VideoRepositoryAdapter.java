package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.VideoJpaRepository;
import java.util.Optional;
import java.util.UUID;
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
}
