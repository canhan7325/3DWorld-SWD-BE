package com.project.w_3d_world.features.catalog.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.CatalogPage;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.ProductDetail;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.ReviewResponse;
import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.catalog.repository.CatalogReadRepository;
import com.project.w_3d_world.features.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirements
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Public physical product discovery and detail APIs.")
public class CatalogController {
    private final CatalogService catalog;
    private final CatalogReadRepository reads;

    @GetMapping("/featured")
    @Operation(summary = "Get featured physical products", description = "Return public products with a configured badge.")
    public ApiResponse<CatalogPage> featured(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(catalog.search(null, null, null, null, "best_selling", "PHYSICAL",
                null, true, page, size));
    }

    @GetMapping("/{productId}/reviews")
    @Operation(summary = "Get published product reviews", description = "Return only public reviews with stable pagination.")
    public ApiResponse<MarketplacePage<
            ReviewResponse>> reviews(
            @PathVariable int productId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reads.reviews(productId, page, size));
    }

    @GetMapping
    @Operation(
            summary = "Search public catalog",
            description = "Search published physical products. Filters: keyword, category, minPrice, maxPrice. Sort: "
                    + "newest, price_asc, price_desc, rating or best_selling. Page starts at 0; size is 1-100.")
    public ApiResponse<CatalogPage> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "PHYSICAL") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(catalog.search(keyword, category, minPrice, maxPrice, sort, type, null, false, page, size));
    }

    @GetMapping("/{productId}")
    @Operation(
            summary = "Get product detail",
            description = "Return product media, variants, prices and current stock availability.")
    public ApiResponse<ProductDetail> detail(@PathVariable int productId) {
        return ApiResponse.success(catalog.detail(productId));
    }
}
