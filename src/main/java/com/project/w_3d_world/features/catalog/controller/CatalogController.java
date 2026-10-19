package com.project.w_3d_world.features.catalog.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.CatalogPage;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.ProductDetail;
import com.project.w_3d_world.features.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Public physical product discovery and detail APIs.")
public class CatalogController {
    private final CatalogService catalog;

    @GetMapping
    @Operation(
            summary = "Search public catalog",
            description = "Search, filter and page through published products from active storefronts.")
    public ApiResponse<CatalogPage> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(catalog.search(keyword, category, page, size));
    }

    @GetMapping("/{productId}")
    @Operation(
            summary = "Get product detail",
            description = "Return product media, variants, prices and current stock availability.")
    public ApiResponse<ProductDetail> detail(@PathVariable int productId) {
        return ApiResponse.success(catalog.detail(productId));
    }
}
