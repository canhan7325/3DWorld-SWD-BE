package com.project.w_3d_world.features.catalog.service;

import com.project.w_3d_world.features.catalog.dto.CatalogResponses.*;
import java.math.BigDecimal;

public interface CatalogService {
    CatalogPage search(String keyword, String category, int page, int size);
    CatalogPage search(String keyword, String category, BigDecimal minPrice, BigDecimal maxPrice,
                       String sort, String type, Integer storefrontId, boolean featured, int page, int size);
    ProductDetail detail(int productId);
}
