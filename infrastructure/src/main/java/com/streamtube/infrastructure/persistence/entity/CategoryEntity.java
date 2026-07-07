package com.streamtube.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persistence model for a video category. Read-only: rows are seeded by migration. */
@Entity
@Table(name = "categories")
public class CategoryEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 60)
  private String name;

  @Column(nullable = false, unique = true, length = 60)
  private String slug;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CategoryEntity() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
