package com.streamtube.api.web;

import com.streamtube.api.web.dto.CategoryDtos.CategoryResponse;
import com.streamtube.application.category.ListCategoriesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "categories", description = "Platform video categories")
public class CategoriesController {

  private final ListCategoriesUseCase listCategories;

  public CategoriesController(ListCategoriesUseCase listCategories) {
    this.listCategories = listCategories;
  }

  @GetMapping
  @Operation(summary = "List the platform's video categories")
  public List<CategoryResponse> list() {
    return listCategories.execute().stream()
        .map(c -> new CategoryResponse(c.id(), c.name(), c.slug()))
        .toList();
  }
}
