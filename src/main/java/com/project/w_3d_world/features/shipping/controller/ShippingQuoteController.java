package com.project.w_3d_world.features.shipping.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.shipping.dto.ShippingQuoteDtos.*;
import com.project.w_3d_world.features.shipping.service.ShippingQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shipping quotes", description = "Box recommendations and GHTK shipping estimates before checkout.")
public class ShippingQuoteController {
    private final ShippingQuoteService quotes;

    @PostMapping("/quote")
    @Operation(summary = "Suggest boxes and quote GHTK shipping",
            description = "Provide selected cartItemIds and shippingAddressId. Each seller's default address is the pickup. "
                    + "Requires variant weight/dimensions and active packaging profiles. Uses padding on every face, "
                    + "floor rotation only and no stacking. Each parcel is quoted independently, not as a consolidated "
                    + "multi-package consignment. Pass returned quoteToken to checkout within its expiry. "
                    + "Packaging cost is informational/internal; totalShippingFee includes carrier fee and insurance. "
                    + "Uses original province and ward (two-level geography), without legacy district mappings. "
                    + "A valid fee is accepted even when carrierFees.delivery is false; delivery is informational only. "
                    + "No shipment is created and stock is not reserved by this endpoint.")
    public ApiResponse<QuoteResponse> quote(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody QuoteRequest request) {
        return ApiResponse.success(quotes.quote(user.userId(), request));
    }
}
