package com.project.w_3d_world.features.catalog.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.catalog.dto.SellerProductDtos.*;
import com.project.w_3d_world.features.catalog.service.SellerProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Seller products", description = "Manage physical products belonging to the signed-in seller's storefront.")
public class SellerProductController {
    private final SellerProductService products;

    @GetMapping
    @Operation(summary = "List own products", description = "Return own products including drafts and moderation decisions.")
    public ApiResponse<MarketplacePage<ProductView>> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(products.list(user.userId(), page, size));
    }

    @PostMapping
    @Operation(summary = "Create product draft", description = "Create a draft with optional variants. Only an active verified shop can create products.")
    public ApiResponse<ProductView> create(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(products.create(user.userId(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get own product", description = "Return product content, media, variants and moderation revision.")
    public ApiResponse<ProductView> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id) {
        return ApiResponse.success(products.get(user.userId(), id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update own product", description = "Changed content returns to DRAFT and requires a new moderation submission.")
    public ApiResponse<ProductView> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id,
                                           @Valid @RequestBody ProductPatch request) {
        return ApiResponse.success(products.update(user.userId(), id, request));
    }

    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload product image", description = "Upload a JPEG/PNG up to 5 MB to Cloudinary Products. sortOrder controls display order; optional "
                    + "variantId must belong to this product.")
    public ApiResponse<ProductView> media(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id,
            @RequestPart("file") MultipartFile file, @RequestParam(required = false) Integer variantId,
            @RequestParam(defaultValue = "0") int sortOrder) {
        return ApiResponse.success(products.addMedia(user.userId(), id, variantId, sortOrder, file));
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @Operation(summary = "Remove product image", description = "Detach an image belonging to this product and require moderation again.")
    public ApiResponse<ProductView> removeMedia(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable int id, @PathVariable int mediaId) {
        return ApiResponse.success(products.removeMedia(user.userId(), id, mediaId));
    }

    @PostMapping("/{id}/variants")
    @Operation(summary = "Add product variant", description = "Create a variant with globally unique SKU and nonnegative price/stock.")
    public ApiResponse<ProductView> variant(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id,
                                            @Valid @RequestBody CreateVariantRequest request) {
        return ApiResponse.success(products.addVariant(user.userId(), id, request));
    }

    @PatchMapping("/{id}/variants/{variantId}")
    @Operation(summary = "Update product variant", description = "Edit own variant content and pricing. Content changes require moderation again.")
    public ApiResponse<ProductView> updateVariant(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id, @PathVariable int variantId, @Valid @RequestBody VariantPatch request) {
        return ApiResponse.success(products.updateVariant(user.userId(), id, variantId, request));
    }

    @PatchMapping("/{id}/variants/{variantId}/stock")
    @Operation(summary = "Adjust variant stock", description = "Set total stock under a row lock. Total cannot be below reserved quantity. Include the adjustment reason.")
    public ApiResponse<ProductView> stock(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id, @PathVariable int variantId, @Valid @RequestBody StockRequest request) {
        return ApiResponse.success(products.stock(user.userId(), id, variantId, request));
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    @Operation(summary = "Deactivate variant", description = "Soft remove a variant while preserving existing order references. Reserved variants cannot be removed.")
    public ApiResponse<ProductView> removeVariant(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable int id, @PathVariable int variantId) {
        return ApiResponse.success(products.removeVariant(user.userId(), id, variantId));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit product for moderation", description = "Require title, category, image and active variant, then queue manual review.")
    public ApiResponse<ProductView> submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id) {
        return ApiResponse.success(products.action(user.userId(), id, "submit"));
    }

    @PatchMapping("/{id}/unpublish")
    @Operation(summary = "Unpublish product", description = "Stop new purchases without changing existing orders or their snapshots.")
    public ApiResponse<ProductView> unpublish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id) {
        return ApiResponse.success(products.action(user.userId(), id, "unpublish"));
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive product", description = "Retain the product and historical references while removing it from sale.")
    public ApiResponse<ProductView> archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id) {
        return ApiResponse.success(products.action(user.userId(), id, "archive"));
    }
}
