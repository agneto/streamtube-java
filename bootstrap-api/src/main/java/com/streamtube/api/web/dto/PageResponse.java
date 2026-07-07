package com.streamtube.api.web.dto;

import com.streamtube.domain.shared.PageResult;
import java.util.List;
import java.util.function.Function;

/** Shared page envelope for every paginated listing. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

  public static <T, R> PageResponse<R> from(PageResult<T> result, Function<T, R> mapper) {
    return new PageResponse<>(
        result.items().stream().map(mapper).toList(),
        result.page(),
        result.size(),
        result.totalItems(),
        result.totalPages());
  }
}
