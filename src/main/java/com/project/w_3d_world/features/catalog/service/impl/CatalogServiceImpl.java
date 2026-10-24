package com.project.w_3d_world.features.catalog.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.*;
import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.catalog.repository.*;
import com.project.w_3d_world.features.catalog.service.CatalogService;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {
    private final ProductRepository products;
    private final ProductMediaRepository media;
    private final ProductVariantRepository variants;
    private final StorefrontRepository storefronts;
    private final CatalogReadRepository reads;

    @Override
    public CatalogPage search(String keyword, String category, int page, int size) {
        return search(keyword, category, null, null, "newest", "PHYSICAL", null, false, page, size);
    }

    @Override
    public CatalogPage search(String keyword, String category, BigDecimal minPrice, BigDecimal maxPrice,
                              String sort, String type, Integer storefrontId, boolean featured, int page, int size) {
        if (!"PHYSICAL".equalsIgnoreCase(type)) {
            throw ApiException.invalidRequest("Only PHYSICAL catalog is available. Digital commerce is not enabled.");
        }
        return reads.search(keyword, category, minPrice, maxPrice, sort, storefrontId, featured, page, size);
    }

    @Override
    public ProductDetail detail(int productId) {
        ProductSummary summary = reads.summary(productId);
        var product = products.findById(productId).orElseThrow();
        var storefront = storefronts.findById(product.getStorefrontId()).orElseThrow();
        List<String> urls = media.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(item -> item.getMediaUrl()).toList();
        var productVariants = variants.findByProductIdOrderByIdAsc(productId).stream()
                .filter(variant -> variant.isActive() && "ACTIVE".equals(variant.getStatus()))
                .map(VariantResponse::from).toList();
        return new ProductDetail(summary, urls, productVariants, product.getDescription(), product.getSpecifications(),
                product.getShippingClass(), new StorefrontSummary(storefront.getId(), storefront.getName(),
                storefront.getDescription(), storefront.getStatus().name(), storefront.canPublish()), summary.available());
    }
}
