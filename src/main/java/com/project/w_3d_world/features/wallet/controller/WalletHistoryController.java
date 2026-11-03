package com.project.w_3d_world.features.wallet.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.wallet.dto.response.PayoutHistoryResponse;
import com.project.w_3d_world.features.wallet.dto.response.WalletLedgerResponse;
import com.project.w_3d_world.features.wallet.service.WalletHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
@SecurityRequirement(name="bearerAuth")
public class WalletHistoryController {
    private final WalletHistoryService history;

    @GetMapping({"/payouts","/wallet/payouts"})
    @Operation(summary="Own payout history",description="Newest first. Bank details are masked and reflect the linked bank record; reason contains the latest recorded payout reason.")
    public ApiResponse<List<PayoutHistoryResponse>> payouts(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ApiResponse.success(history.payouts(user.userId(),page,size));
    }
    @GetMapping("/wallet/ledgers")
    @Operation(summary="Own wallet balance movements",description="Newest first. Read balanceBucket together with signedAmount: holds and releases move money between buckets. Revenue release uses existing entryType SELLER_REVENUE_RELEASE with PENDING debit and AVAILABLE credit.")
    public ApiResponse<List<WalletLedgerResponse>> ledgers(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ApiResponse.success(history.ledgers(user.userId(),page,size));
    }
}
