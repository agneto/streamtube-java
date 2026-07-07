package com.streamtube.application.category;

import com.streamtube.domain.category.Category;
import com.streamtube.domain.category.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the platform's fixed, migration-seeded category catalog. */
@Service
public class ListCategoriesUseCase {

  private final CategoryRepository categoryRepository;

  public ListCategoriesUseCase(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  @Transactional(readOnly = true)
  public List<Category> execute() {
    return categoryRepository.findAll();
  }
}
