package com.project.w_3d_world.features.storefront.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.storefront.dto.AdminStorefrontDtos.ListResponse;
import com.project.w_3d_world.features.storefront.dto.AdminStorefrontDtos.SubscriptionRow;
import com.project.w_3d_world.features.storefront.service.AdminStorefrontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/storefronts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin storefronts", description = "Read-only list of every storefront and the plan it is renting.")
public class AdminStorefrontController {
    private final AdminStorefrontService service;

    @GetMapping
    @Operation(summary = "List all storefronts with their plan",
            description = "plan: ACTIVE | EXPIRED | PENDING_PAYMENT | NONE. status: ACTIVE | INACTIVE | SUSPENDED. "
                    + "sort: newest | oldest | expiring | revenue | name. Summary counts ignore filters.")
    public ApiResponse<ListResponse> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status, @RequestParam(required = false) String plan,
            @RequestParam(defaultValue = "newest") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(keyword, status, plan, sort, page, size));
    }

    @GetMapping("/{storefrontId}/subscriptions")
    @Operation(summary = "Subscription history of one storefront", description = "Newest first, including unpaid ones.")
    public ApiResponse<List<SubscriptionRow>> subscriptions(@PathVariable int storefrontId) {
        return ApiResponse.success(service.subscriptions(storefrontId));
    }
}
