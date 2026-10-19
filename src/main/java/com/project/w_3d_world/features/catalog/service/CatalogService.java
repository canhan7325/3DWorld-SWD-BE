package com.project.w_3d_world.features.catalog.service;

import com.project.w_3d_world.features.catalog.dto.CatalogResponses.CatalogPage;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.ProductDetail;

public interface CatalogService {
    CatalogPage search(String keyword, String category, int page, int size);

    ProductDetail detail(int productId);
}
