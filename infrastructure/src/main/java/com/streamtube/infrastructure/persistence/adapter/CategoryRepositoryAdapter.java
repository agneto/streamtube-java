package com.streamtube.infrastructure.persistence.adapter;

import com.streamtube.domain.category.Category;
import com.streamtube.domain.category.CategoryRepository;
import com.streamtube.infrastructure.persistence.mapper.PersistenceMapper;
import com.streamtube.infrastructure.persistence.repository.CategoryJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepositoryAdapter implements CategoryRepository {

  private final CategoryJpaRepository jpa;

  public CategoryRepositoryAdapter(CategoryJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Category> findAll() {
    return jpa.findAllByOrderByNameAsc().stream().map(PersistenceMapper::toDomain).toList();
  }

  @Override
  public boolean existsById(UUID id) {
    return jpa.existsById(id);
  }
}
