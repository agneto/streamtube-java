package com.streamtube.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox row for an asynchronous storage deletion. Lives in the SHARED entity package (not the
 * social slice): the worker's persistence unit drains this table.
 */
@Entity
@Table(name = "storage_cleanups")
public class StorageCleanupEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 600)
  private String prefix;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected StorageCleanupEntity() {}

  public StorageCleanupEntity(UUID id, String prefix, Instant createdAt) {
    this.id = id;
    this.prefix = prefix;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getPrefix() {
    return prefix;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
