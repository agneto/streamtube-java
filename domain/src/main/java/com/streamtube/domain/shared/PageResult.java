package com.streamtube.domain.shared;

import java.util.List;

/**
 * Framework-free page of results for repository ports (keeps Spring's Pageable out of the
 * application layer). {@code page} is zero-based.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

  public int totalPages() {
    return size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
  }

  public <R> PageResult<R> map(java.util.function.Function<T, R> mapper) {
    return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalItems);
  }
}
