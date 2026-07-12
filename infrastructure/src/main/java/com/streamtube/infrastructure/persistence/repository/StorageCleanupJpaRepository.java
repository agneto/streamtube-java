package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.infrastructure.persistence.entity.StorageCleanupEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageCleanupJpaRepository extends JpaRepository<StorageCleanupEntity, UUID> {

  List<StorageCleanupEntity> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
