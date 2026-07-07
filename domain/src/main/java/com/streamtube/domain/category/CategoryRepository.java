package com.streamtube.domain.category;

import java.util.List;
import java.util.UUID;

/** Output port for category persistence (read-only: the list is seeded by migration). */
public interface CategoryRepository {

  List<Category> findAll();

  boolean existsById(UUID id);
}
