package com.project.w_3d_world.features.catalog.service;

import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.catalog.dto.request.CreateVariantRequest;
import com.project.w_3d_world.features.catalog.dto.request.DecisionRequest;
import com.project.w_3d_world.features.catalog.dto.request.ProductPatch;
import com.project.w_3d_world.features.catalog.dto.request.ProductRequest;
import com.project.w_3d_world.features.catalog.dto.request.StockRequest;
import com.project.w_3d_world.features.catalog.dto.request.VariantPatch;
import com.project.w_3d_world.features.catalog.dto.response.ProductView;
import org.springframework.web.multipart.MultipartFile;

public interface SellerProductService {

    MarketplacePage<ProductView> list(int userId, int page, int size);
    ProductView get(int userId, int id);
    ProductView create(int userId, ProductRequest request);
    ProductView update(int userId, int id, ProductPatch request);
    ProductView addVariant(int userId, int id, CreateVariantRequest request);
    ProductView updateVariant(int userId, int id, int variantId, VariantPatch request);
    ProductView stock(int userId, int id, int variantId, StockRequest request);
    ProductView removeVariant(int userId, int id, int variantId);
    ProductView addMedia(int userId, int id, Integer variantId, int sortOrder, MultipartFile file);
    ProductView removeMedia(int userId, int id, int mediaId);
    ProductView action(int userId, int id, String action);
    ProductView moderate(int reviewerId, int id, DecisionRequest request, boolean approved);
    ProductView moderationDetail(int reviewerId, int id);
    MarketplacePage<ProductView> pending(int reviewerId, int page, int size);
}
