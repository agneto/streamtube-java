package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.domain.video.Visibility;
import com.streamtube.infrastructure.persistence.entity.VideoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoJpaRepository extends JpaRepository<VideoEntity, UUID> {

  Optional<VideoEntity> findBySlug(String slug);

  boolean existsBySlug(String slug);

  Page<VideoEntity> findByChannelIdOrderByCreatedAtDesc(UUID channelId, Pageable pageable);

  Page<VideoEntity> findByChannelIdAndVisibilityAndPublishedAtNotNullOrderByPublishedAtDesc(
      UUID channelId, Visibility visibility, Pageable pageable);
}
