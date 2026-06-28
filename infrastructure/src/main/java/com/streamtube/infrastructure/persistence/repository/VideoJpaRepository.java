package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.infrastructure.persistence.entity.VideoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoJpaRepository extends JpaRepository<VideoEntity, UUID> {

  Optional<VideoEntity> findBySlug(String slug);

  boolean existsBySlug(String slug);
}
