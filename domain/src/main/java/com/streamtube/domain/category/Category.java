package com.streamtube.domain.category;

import java.time.Instant;
import java.util.UUID;

/** Pure domain entity for a platform video category (fixed, seeded list — no user CRUD). */
public class Category {

  private final UUID id;
  private final String name;
  private final String slug;
  private final Instant createdAt;

  public Category(UUID id, String name, String slug, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.slug = slug;
    this.createdAt = createdAt;
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String slug() {
    return slug;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
