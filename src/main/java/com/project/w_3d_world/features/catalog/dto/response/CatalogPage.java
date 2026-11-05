package com.project.w_3d_world.features.catalog.dto.response;

import java.util.List;

public record CatalogPage(List<ProductSummary> items, int page, int size, long totalElements, int totalPages) {}
