package com.streamtube.api.web.dto;

import java.util.UUID;

/** Response payloads for the category endpoints. */
public final class CategoryDtos {

  private CategoryDtos() {}

  public record CategoryResponse(UUID id, String name, String slug) {}
}
