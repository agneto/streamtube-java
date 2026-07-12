package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.application.port.out.StorageCleanupQueue;
import com.streamtube.infrastructure.persistence.entity.StorageCleanupEntity;
import com.streamtube.infrastructure.persistence.repository.StorageCleanupJpaRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class StorageCleanupQueueAdapter implements StorageCleanupQueue {

  private final StorageCleanupJpaRepository jpa;
  private final Clock clock;

  public StorageCleanupQueueAdapter(StorageCleanupJpaRepository jpa, Clock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public void enqueue(String prefix) {
    jpa.save(new StorageCleanupEntity(UUID.randomUUID(), prefix, clock.instant()));
  }

  @Override
  public List<PendingCleanup> due(int limit) {
    return jpa.findAllByOrderByCreatedAtAsc(PageRequest.of(0, limit)).stream()
        .map(e -> new PendingCleanup(e.getId(), e.getPrefix()))
        .toList();
  }

  @Override
  public void remove(UUID id) {
    jpa.deleteById(id);
  }
}
