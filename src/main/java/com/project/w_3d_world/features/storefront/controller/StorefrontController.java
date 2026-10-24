package com.project.w_3d_world.features.storefront.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.catalog.dto.CatalogResponses.CatalogPage;
import com.project.w_3d_world.features.catalog.service.CatalogService;
import com.project.w_3d_world.features.storefront.dto.StorefrontDtos.*;
import com.project.w_3d_world.features.storefront.service.StorefrontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/storefronts")
@RequiredArgsConstructor
@Tag(name = "Storefronts", description = "Public storefront discovery and authenticated owner management.")
public class StorefrontController {
    private final StorefrontService stores;
    private final CatalogService catalog;

    @SecurityRequirements
    @GetMapping("/{storefrontId}")
    @Operation(summary = "Get public storefront", description = "Expose public shop fields and current trading availability.")
    public ApiResponse<ShopResponse> get(@PathVariable int storefrontId) {
        return ApiResponse.success(stores.publicShop(storefrontId));
    }

    @SecurityRequirements
    @GetMapping("/{storefrontId}/products")
    @Operation(summary = "Get public storefront products", description = "Return only approved public listings from this shop.")
    public ApiResponse<CatalogPage> products(@PathVariable int storefrontId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        stores.publicShop(storefrontId);
        return ApiResponse.success(catalog.search(keyword, category, minPrice, maxPrice, sort,
                "PHYSICAL", storefrontId, false, page, size));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create storefront", description = "Create one inactive shop after approved seller verification. Payment is required for activation.")
    public ApiResponse<ShopResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody CreateRequest request) {
        return ApiResponse.success(stores.create(user.userId(), request));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get own storefront", description = "Return the signed-in owner's shop and its current availability.")
    public ApiResponse<ShopResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(stores.mine(user.userId()));
    }

    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update own storefront", description = "Update name and description without changing verification or subscription status.")
    public ApiResponse<ShopResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.success(stores.update(user.userId(), request));
    }

    @GetMapping("/me/subscription")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get own subscription", description = "Return configured monthly fee, expiry and latest/pending subscription.")
    public ApiResponse<SubscriptionOverview> subscription(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(stores.subscription(user.userId()));
    }

    @PostMapping({"/me/subscriptions", "/me/subscriptions/renew"})
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create or reuse pending subscription", description = "Pay returned id through POST /api/payments/zalopay with sourceType SUBSCRIPTION and stage "
                    + "SUBSCRIPTION. Only verified payment activates one calendar month in UTC.")
    public ApiResponse<SubscriptionResponse> subscribe(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(stores.subscribe(user.userId()));
    }
}
