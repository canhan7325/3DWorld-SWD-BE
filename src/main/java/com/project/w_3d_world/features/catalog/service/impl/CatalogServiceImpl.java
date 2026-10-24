package com.project.w_3d_world.features.catalog.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.CatalogPage;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.ProductDetail;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.ProductSummary;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.VariantResponse;
import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.repository.ProductMediaRepository;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.catalog.repository.ProductVariantRepository;
import com.project.w_3d_world.features.catalog.service.CatalogService;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
    private final ProductRepository products;
    private final ProductMediaRepository media;
    private final ProductVariantRepository variants;
    private final StorefrontRepository storefronts;

    @Override
    @Transactional(readOnly = true)
    public CatalogPage search(String keyword, String category, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidRequest("Invalid page or size.");
        }
        var result = products.searchPublic(
                normalize(keyword), normalize(category),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<ProductSummary> items = result.getContent().stream()
                .map(ProductSummary::from)
                .toList();
        return new CatalogPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetail detail(int productId) {
        Product product = products.findByIdAndStatus(productId, "PUBLISHED")
                .filter(this::publicStorefront)
                .orElseThrow(() -> ApiException.invalidRequest("Product is not publicly available."));
        List<String> mediaUrls = media.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(item -> item.getMediaUrl())
                .toList();
        var productVariants = variants.findByProductIdOrderByIdAsc(productId).stream()
                .map(VariantResponse::from)
                .toList();
        return new ProductDetail(ProductSummary.from(product), mediaUrls, productVariants);
    }

    private boolean publicStorefront(Product product) {
        return storefronts.findById(product.getStorefrontId())
                .map(storefront -> storefront.canPublish())
                .orElse(false);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
