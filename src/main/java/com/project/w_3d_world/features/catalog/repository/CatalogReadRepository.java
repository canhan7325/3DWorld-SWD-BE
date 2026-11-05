package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.catalog.dto.response.CatalogPage;
import com.project.w_3d_world.features.catalog.dto.response.ProductSummary;
import com.project.w_3d_world.features.catalog.dto.response.ReviewResponse;
import java.math.BigDecimal;

public interface CatalogReadRepository {
    CatalogPage search(String keyword, String category, BigDecimal minPrice, BigDecimal maxPrice,
                                  String sort, Integer storefrontId, boolean featured, int page, int size);
    ProductSummary summary(int id);
    MarketplacePage<ReviewResponse> reviews(int id, int page, int size);
}
